/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.lang.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final boolean reportHistogramBuckets;

    public SignalFxMeterRegistry(SignalFxConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY);
    }

    public SignalFxMeterRegistry(SignalFxConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;

        URI apiUri = URI.create(config.uri());
        int port = apiUri.getPort();
        if (port == -1) {
            if ("http" .equals(apiUri.getScheme())) {
                port = 80;
            } else if ("https" .equals(apiUri.getScheme())) {
                port = 443;
            }
        }

        SignalFxReceiverEndpoint signalFxEndpoint = new SignalFxEndpoint(apiUri.getScheme(), apiUri.getHost(), port);
        this.dataPointReceiverFactory = new HttpDataPointProtobufReceiverFactory(signalFxEndpoint);
        this.eventReceiverFactory = new HttpEventProtobufReceiverFactory(signalFxEndpoint);
        this.reportHistogramBuckets = config.reportHistogramBuckets();

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
        // TODO: Investigate if the default HistogramGauges metrics should be removed:
        //  * Adds a new gauge ".histogram" with "le" tag when slo buckets defined.
        //  * Adds a new gauge ".percentile" with "phi" tag when percentiles buckets are defined.
        return super.newTimer(id, updateConfig(distributionStatisticConfig), pauseDetector);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        // TODO: Investigate if the default HistogramGauges metrics should be removed:
        //  * Adds a new gauge ".histogram" with "le" tag when slo buckets defined.
        //  * Adds a new gauge ".percentile" with "phi" tag when percentiles buckets are defined.
        return super.newDistributionSummary(id, updateConfig(distributionStatisticConfig), scale);
    }

    private Stream<SignalFxProtocolBuffers.DataPoint.Builder> addMeter(Meter meter) {
        return stream(meter.measure().spliterator(), false).flatMap(measurement -> {
            String statSuffix = NamingConvention.camelCase.tagKey(measurement.getStatistic().toString());
            switch (measurement.getStatistic()) {
                case TOTAL:
                case TOTAL_TIME:
                case COUNT:
                case DURATION:
                    return Stream.of(addDatapoint(meter.getId(), COUNTER, statSuffix,
                            null, measurement.getValue()));
                case MAX:
                case VALUE:
                case UNKNOWN:
                case ACTIVE_TASKS:
                    return Stream.of(addDatapoint(meter.getId(), GAUGE, statSuffix,
                            null, measurement.getValue()));
            }
            return Stream.empty();
        });
    }

    private SignalFxProtocolBuffers.DataPoint.Builder addDatapoint(Meter.Id meterId,
            SignalFxProtocolBuffers.MetricType metricType, @Nullable String statSuffix,
            @Nullable Tag extraTag, Number value) {
        SignalFxProtocolBuffers.Datum.Builder datumBuilder = SignalFxProtocolBuffers.Datum.newBuilder();
        SignalFxProtocolBuffers.Datum datum = (value instanceof Double ?
                datumBuilder.setDoubleValue((Double) value) :
                datumBuilder.setIntValue(value.longValue())
        ).build();

        String metricName = config().namingConvention().name(statSuffix == null ?
                        meterId.getName() : meterId.getName() + statSuffix,
                meterId.getType(), meterId.getBaseUnit());

        SignalFxProtocolBuffers.DataPoint.Builder dataPointBuilder = SignalFxProtocolBuffers.DataPoint.newBuilder()
                .setMetric(metricName)
                .setMetricType(metricType)
                .setValue(datum);

        for (Tag tag : getConventionTags(meterId)) {
            dataPointBuilder.addDimensions(SignalFxProtocolBuffers.Dimension.newBuilder()
                    .setKey(tag.getKey())
                    .setValue(tag.getValue())
                    .build());
        }

        if (extraTag != null) {
            dataPointBuilder.addDimensions(SignalFxProtocolBuffers.Dimension.newBuilder()
                    .setKey(config().namingConvention().tagKey(extraTag.getKey()))
                    .setValue(config().namingConvention().tagValue(extraTag.getValue()))
                    .build());
        }

        return dataPointBuilder;
    }

    private Stream<SignalFxProtocolBuffers.DataPoint.Builder> addLongTaskTimer(LongTaskTimer longTaskTimer) {
        return Stream.of(
                addDatapoint(longTaskTimer.getId(), GAUGE, ".activeTasks",
                        null, longTaskTimer.activeTasks()),
                addDatapoint(longTaskTimer.getId(), COUNTER, ".duration",
                        null, longTaskTimer.duration(getBaseTimeUnit()))
        );
    }

    private Stream<SignalFxProtocolBuffers.DataPoint.Builder> addTimeGauge(TimeGauge timeGauge) {
        return Stream.of(addDatapoint(timeGauge.getId(), GAUGE, null, null,
                timeGauge.value(getBaseTimeUnit())));
    }

    private Stream<SignalFxProtocolBuffers.DataPoint.Builder> addGauge(Gauge gauge) {
        return Stream.of(addDatapoint(gauge.getId(), GAUGE, null,
                null, gauge.value()));
    }

    private Stream<SignalFxProtocolBuffers.DataPoint.Builder> addCounter(Counter counter) {
        return Stream.of(addDatapoint(counter.getId(), COUNTER, null,
                null, counter.count()));
    }

    private Stream<SignalFxProtocolBuffers.DataPoint.Builder> addFunctionCounter(FunctionCounter counter) {
        return Stream.of(addDatapoint(counter.getId(), COUNTER, null,
                null, counter.count()));
    }

    private Stream<SignalFxProtocolBuffers.DataPoint.Builder> addTimer(Timer timer) {
        return addHistogramSnapshot(timer.getId(), timer.takeSnapshot());
    }

    private Stream<SignalFxProtocolBuffers.DataPoint.Builder> addFunctionTimer(FunctionTimer timer) {
        return Stream.of(
                addDatapoint(timer.getId(), COUNTER, ".count",
                        null, timer.count()),
                addDatapoint(timer.getId(), COUNTER, ".totalTime",
                        null, timer.totalTime(getBaseTimeUnit())),
                addDatapoint(timer.getId(), GAUGE, ".avg",
                        null, timer.mean(getBaseTimeUnit()))
        );
    }

    private Stream<SignalFxProtocolBuffers.DataPoint.Builder> addDistributionSummary(DistributionSummary summary) {
        return addHistogramSnapshot(summary.getId(), summary.takeSnapshot());
    }

    // VisibleForTesting
    Stream<SignalFxProtocolBuffers.DataPoint.Builder> addHistogramSnapshot(Meter.Id meterId, HistogramSnapshot histogramSnapshot) {
        Stream<SignalFxProtocolBuffers.DataPoint.Builder> basic = Stream.of(
                addDatapoint(meterId, COUNTER, ".count", null, histogramSnapshot.count()),
                addDatapoint(meterId, COUNTER, ".totalTime", null,
                        histogramSnapshot.total(getBaseTimeUnit())),
                addDatapoint(meterId, GAUGE, ".avg", null,
                        histogramSnapshot.mean(getBaseTimeUnit())),
                addDatapoint(meterId, GAUGE, ".max",
                        null, histogramSnapshot.max(getBaseTimeUnit())));
        if (!this.reportHistogramBuckets) {
            return basic;
        }
        CountAtBucket[] histogramCounts = histogramSnapshot.histogramCounts();
        List<SignalFxProtocolBuffers.DataPoint.Builder> buckets =
                new ArrayList<>(histogramCounts.length);
        for (CountAtBucket countAtBucket: histogramCounts) {
            buckets.add(addDatapoint(meterId, CUMULATIVE_COUNTER, "_bucket",
                    new ImmutableTag("upper_bound",
                            isPositiveInf(countAtBucket.bucket()) ? "+Inf" : DoubleFormat.wholeOrDecimal(countAtBucket.bucket(getBaseTimeUnit()))),
                    countAtBucket.count()));
        }
        return Stream.concat(basic, buckets.stream());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    private static boolean isPositiveInf(double bucket) {
        return bucket == Double.POSITIVE_INFINITY || bucket == Double.MAX_VALUE;
    }


    private DistributionStatisticConfig updateConfig(DistributionStatisticConfig distributionStatisticConfig) {
        // Add the +Inf bucket since the "count" resets every export.
        double[] sla = distributionStatisticConfig.getServiceLevelObjectiveBoundaries();
        if (!reportHistogramBuckets || sla == null) {
            return distributionStatisticConfig;
        }
        double[] buckets = Arrays.copyOf(sla, sla.length + 1);
        buckets[buckets.length - 1] = Double.MAX_VALUE;
        return DistributionStatisticConfig.builder()
                .expiry(Duration.ofDays(365 * 100)) // effectively a lifetime
                .bufferLength(1)
                .serviceLevelObjectives(buckets)
                .build()
                .merge(distributionStatisticConfig);
    }
}
