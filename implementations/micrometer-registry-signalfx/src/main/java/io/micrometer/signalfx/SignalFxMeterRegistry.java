/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.signalfx;

import com.signalfx.endpoint.SignalFxEndpoint;
import com.signalfx.endpoint.SignalFxReceiverEndpoint;
import com.signalfx.metrics.auth.StaticAuthToken;
import com.signalfx.metrics.connection.HttpDataPointProtobufReceiverFactory;
import com.signalfx.metrics.connection.HttpEventProtobufReceiverFactory;
import com.signalfx.metrics.errorhandler.OnSendErrorHandler;
import com.signalfx.metrics.flush.AggregateMetricSender;
import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType.COUNTER;
import static com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType.CUMULATIVE_COUNTER;
import static com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType.GAUGE;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link StepMeterRegistry} for SignalFx.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @since 1.0.0
 */
public class SignalFxMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("signalfx-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(SignalFxMeterRegistry.class);
    private final SignalFxConfig config;
    private final HttpDataPointProtobufReceiverFactory dataPointReceiverFactory;
    private final HttpEventProtobufReceiverFactory eventReceiverFactory;
    private final Set<OnSendErrorHandler> onSendErrorHandlerCollection = Collections.singleton(
            metricError -> this.logger.warn("failed to send metrics: {}", metricError.getMessage()));
    private final boolean publishCumulativeHistogram;

    public SignalFxMeterRegistry(SignalFxConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY);
    }

    public SignalFxMeterRegistry(SignalFxConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;

        URI apiUri = URI.create(config.uri());
        int port = apiUri.getPort();
        if (port == -1) {
            if ("http".equals(apiUri.getScheme())) {
                port = 80;
            } else if ("https".equals(apiUri.getScheme())) {
                port = 443;
            }
        }

        SignalFxReceiverEndpoint signalFxEndpoint = new SignalFxEndpoint(apiUri.getScheme(), apiUri.getHost(), port);
        this.dataPointReceiverFactory = new HttpDataPointProtobufReceiverFactory(signalFxEndpoint);
        this.eventReceiverFactory = new HttpEventProtobufReceiverFactory(signalFxEndpoint);
        this.publishCumulativeHistogram = config.publishCumulativeHistogram();

        config().namingConvention(new SignalFxNamingConvention());

        start(threadFactory);
    }

    @Override
    protected void publish() {
        final long timestamp = clock.wallTime();

        AggregateMetricSender metricSender = new AggregateMetricSender(this.config.source(),
                this.dataPointReceiverFactory, this.eventReceiverFactory,
                new StaticAuthToken(this.config.accessToken()), this.onSendErrorHandlerCollection);

        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            try (AggregateMetricSender.Session session = metricSender.createSession()) {
                batch.stream()
                        .map(meter -> meter.match(
                                this::addGauge,
                                this::addCounter,
                                this::addTimer,
                                this::addDistributionSummary,
                                this::addLongTaskTimer,
                                this::addTimeGauge,
                                this::addFunctionCounter,
                                this::addFunctionTimer,
                                this::addMeter))
                        .flatMap(builders -> builders.map(builder -> builder.setTimestamp(timestamp).build()))
                        .forEach(session::setDatapoint);

                logger.debug("successfully sent {} metrics to SignalFx.", batch.size());
            } catch (Throwable e) {
                logger.warn("failed to send metrics", e);
            }
        }
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        if (!publishCumulativeHistogram) {
            return super.newTimer(id, distributionStatisticConfig, pauseDetector);
        }
        Timer timer = new SignalfxTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(), config.step().toMillis());
        HistogramGauges.registerWithCommonFormat(timer, this);
        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        if (!publishCumulativeHistogram) {
            return super.newDistributionSummary(id, distributionStatisticConfig, scale);
        }
        DistributionSummary summary = new SignalfxDistributionSummary(id, clock, distributionStatisticConfig, scale, config.step().toMillis());
        HistogramGauges.registerWithCommonFormat(summary, this);
        return summary;
    }

    Stream<SignalFxProtocolBuffers.DataPoint.Builder> addMeter(Meter meter) {
        return stream(meter.measure().spliterator(), false).flatMap(measurement -> {
            String statSuffix = NamingConvention.camelCase.tagKey(measurement.getStatistic().toString());
            switch (measurement.getStatistic()) {
                case TOTAL:
                case TOTAL_TIME:
                case COUNT:
                case DURATION:
                    return Stream.of(addDatapoint(meter, COUNTER, statSuffix, measurement.getValue()));
                case MAX:
                case VALUE:
                case UNKNOWN:
                case ACTIVE_TASKS:
                    return Stream.of(addDatapoint(meter, GAUGE, statSuffix, measurement.getValue()));
            }
            return Stream.empty();
        });
    }

    private SignalFxProtocolBuffers.DataPoint.Builder addDatapoint(Meter meter, SignalFxProtocolBuffers.MetricType metricType, @Nullable String statSuffix, Number value) {
        SignalFxProtocolBuffers.Datum.Builder datumBuilder = SignalFxProtocolBuffers.Datum.newBuilder();
        SignalFxProtocolBuffers.Datum datum = (value instanceof Double ?
                datumBuilder.setDoubleValue((Double) value) :
                datumBuilder.setIntValue(value.longValue())
        ).build();

        String metricName = config().namingConvention().name(statSuffix == null ? meter.getId().getName() : meter.getId().getName() + "." + statSuffix,
                meter.getId().getType(), meter.getId().getBaseUnit());

        SignalFxProtocolBuffers.DataPoint.Builder dataPointBuilder = SignalFxProtocolBuffers.DataPoint.newBuilder()
                .setMetric(metricName)
                .setMetricType(metricType)
                .setValue(datum);

        for (Tag tag : getConventionTags(meter.getId())) {
            dataPointBuilder.addDimensions(SignalFxProtocolBuffers.Dimension.newBuilder()
                    .setKey(tag.getKey())
                    .setValue(tag.getValue())
                    .build());
        }

        return dataPointBuilder;
    }

    // VisibleForTesting
    Stream<SignalFxProtocolBuffers.DataPoint.Builder> addLongTaskTimer(LongTaskTimer longTaskTimer) {
        return Stream.of(
                addDatapoint(longTaskTimer, GAUGE, "activeTasks", longTaskTimer.activeTasks()),
                addDatapoint(longTaskTimer, COUNTER, "duration", longTaskTimer.duration(getBaseTimeUnit()))
        );
    }

    Stream<SignalFxProtocolBuffers.DataPoint.Builder> addTimeGauge(TimeGauge timeGauge) {
        return Stream.of(addDatapoint(timeGauge, GAUGE, null, timeGauge.value(getBaseTimeUnit())));
    }

    Stream<SignalFxProtocolBuffers.DataPoint.Builder> addGauge(Gauge gauge) {
        if (publishCumulativeHistogram
                && gauge.getId().syntheticAssociation() != null
                && gauge.getId().getName().endsWith(".histogram")) {
            return Stream.of(addDatapoint(gauge, CUMULATIVE_COUNTER, null, gauge.value()));
        }
        return Stream.of(addDatapoint(gauge, GAUGE, null, gauge.value()));
    }

    Stream<SignalFxProtocolBuffers.DataPoint.Builder> addCounter(Counter counter) {
        return Stream.of(addDatapoint(counter, COUNTER, null, counter.count()));
    }

    Stream<SignalFxProtocolBuffers.DataPoint.Builder> addFunctionCounter(FunctionCounter counter) {
        return Stream.of(addDatapoint(counter, COUNTER, null, counter.count()));
    }

    Stream<SignalFxProtocolBuffers.DataPoint.Builder> addTimer(Timer timer) {
        return Stream.of(
                addDatapoint(timer, COUNTER, "count", timer.count()),
                addDatapoint(timer, COUNTER, "totalTime", timer.totalTime(getBaseTimeUnit())),
                addDatapoint(timer, GAUGE, "avg", timer.mean(getBaseTimeUnit())),
                addDatapoint(timer, GAUGE, "max", timer.max(getBaseTimeUnit()))
        );
    }

    Stream<SignalFxProtocolBuffers.DataPoint.Builder> addFunctionTimer(FunctionTimer timer) {
        return Stream.of(
                addDatapoint(timer, COUNTER, "count", timer.count()),
                addDatapoint(timer, COUNTER, "totalTime", timer.totalTime(getBaseTimeUnit())),
                addDatapoint(timer, GAUGE, "avg", timer.mean(getBaseTimeUnit()))
        );
    }

    Stream<SignalFxProtocolBuffers.DataPoint.Builder> addDistributionSummary(DistributionSummary summary) {
        return Stream.of(
                addDatapoint(summary, COUNTER, "count", summary.count()),
                addDatapoint(summary, COUNTER, "totalTime", summary.totalAmount()),
                addDatapoint(summary, GAUGE, "avg", summary.mean()),
                addDatapoint(summary, GAUGE, "max", summary.max())
        );
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }
}
