/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType.COUNTER;
import static com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType.GAUGE;

/**
 * @author Jon Schneider
 */
public class SignalFxMeterRegistry extends StepMeterRegistry {
    private final Logger logger = LoggerFactory.getLogger(SignalFxMeterRegistry.class);
    private final SignalFxConfig config;
    private final SignalFxReceiverEndpoint signalFxEndpoint;
    private final HttpDataPointProtobufReceiverFactory dataPointReceiverFactory;
    private final HttpEventProtobufReceiverFactory eventReceiverFactory;
    private final Set<OnSendErrorHandler> onSendErrorHandlerCollection = Collections.singleton(
        metricError -> this.logger.warn("failed to send metrics: {}", metricError.getMessage()));

    public SignalFxMeterRegistry(SignalFxConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
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

        this.signalFxEndpoint = new SignalFxEndpoint(apiUri.getScheme(), apiUri.getHost(), port);
        this.dataPointReceiverFactory = new HttpDataPointProtobufReceiverFactory(this.signalFxEndpoint);
        this.eventReceiverFactory = new HttpEventProtobufReceiverFactory(this.signalFxEndpoint);

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
                for (Meter meter : batch) {
                    if (meter instanceof Counter) {
                        addCounter((Counter) meter, session, timestamp);
                    } else if (meter instanceof Timer) {
                        addTimer((Timer) meter, session, timestamp);
                    } else if (meter instanceof DistributionSummary) {
                        addDistributionSummary((DistributionSummary) meter, session, timestamp);
                    } else if (meter instanceof TimeGauge) {
                        addTimeGauge((TimeGauge) meter, session, timestamp);
                    } else if (meter instanceof Gauge) {
                        addGauge((Gauge) meter, session, timestamp);
                    } else if (meter instanceof FunctionTimer) {
                        addFunctionTimer((FunctionTimer) meter, session, timestamp);
                    } else if (meter instanceof FunctionCounter) {
                        addFunctionCounter((FunctionCounter) meter, session, timestamp);
                    } else if (meter instanceof LongTaskTimer) {
                        addLongTaskTimer((LongTaskTimer) meter, session, timestamp);
                    } else {
                        addMeter(meter, session, timestamp);
                    }
                }

                logger.info("successfully sent " + batch.size() + " metrics to SignalFx");
            } catch (Throwable e) {
                logger.warn("failed to send metrics", e);
            }
        }
    }

    private void addMeter(Meter meter, AggregateMetricSender.Session session, long timestamp) {
        for (Measurement measurement : meter.measure()) {
            String statSuffix = NamingConvention.camelCase.tagKey(measurement.getStatistic().toString());

            switch (measurement.getStatistic()) {
                case TOTAL:
                case TOTAL_TIME:
                case COUNT:
                case DURATION:
                    addDatapoint(meter, COUNTER, statSuffix, session, measurement.getValue(), timestamp);
                    break;
                case MAX:
                case VALUE:
                case UNKNOWN:
                case ACTIVE_TASKS:
                    addDatapoint(meter, GAUGE, statSuffix, session, measurement.getValue(), timestamp);
                    break;
            }
        }
    }

    private void addDatapoint(Meter meter, SignalFxProtocolBuffers.MetricType metricType, @Nullable String statSuffix, AggregateMetricSender.Session session, Number value, long timestamp) {
        SignalFxProtocolBuffers.Datum.Builder datumBuilder = SignalFxProtocolBuffers.Datum.newBuilder();
        SignalFxProtocolBuffers.Datum datum = (value instanceof Double ?
                datumBuilder.setDoubleValue((Double) value) :
                datumBuilder.setIntValue((Long) value)
        ).build();

        String metricName = config().namingConvention().name(statSuffix == null ? meter.getId().getName() : meter.getId().getName() + "." + statSuffix,
                meter.getId().getType(), meter.getId().getBaseUnit());

        SignalFxProtocolBuffers.DataPoint.Builder dataPointBuilder = SignalFxProtocolBuffers.DataPoint.newBuilder()
                .setMetric(metricName)
                .setMetricType(metricType)
                .setValue(datum)
                .setTimestamp(timestamp);

        for (Tag tag : getConventionTags(meter.getId())) {
            dataPointBuilder.addDimensions(SignalFxProtocolBuffers.Dimension.newBuilder()
                    .setKey(tag.getKey())
                    .setValue(tag.getValue())
                    .build());
        }

        session.setDatapoint(dataPointBuilder.build());
    }

    private void addLongTaskTimer(LongTaskTimer longTaskTimer, AggregateMetricSender.Session session, long timestamp) {
        addDatapoint(longTaskTimer, GAUGE, "activeTasks", session, longTaskTimer.activeTasks(), timestamp);
        addDatapoint(longTaskTimer, COUNTER, "duration", session, longTaskTimer.duration(getBaseTimeUnit()), timestamp);
    }

    private void addTimeGauge(TimeGauge timeGauge, AggregateMetricSender.Session session, long timestamp) {
        addDatapoint(timeGauge, GAUGE, null, session, timeGauge.value(getBaseTimeUnit()), timestamp);
    }

    private void addGauge(Gauge gauge, AggregateMetricSender.Session session, long timestamp) {
        addDatapoint(gauge, GAUGE, null, session, gauge.value(), timestamp);
    }

    private void addCounter(Counter counter, AggregateMetricSender.Session session, long timestamp) {
        addDatapoint(counter, COUNTER, null, session, counter.count(), timestamp);
    }

    private void addFunctionCounter(FunctionCounter counter, AggregateMetricSender.Session session, long timestamp) {
        addDatapoint(counter, COUNTER, null, session, counter.count(), timestamp);
    }

    private void addTimer(Timer timer, AggregateMetricSender.Session session, long timestamp) {
        addDatapoint(timer, COUNTER, "count", session, timer.count(), timestamp);
        addDatapoint(timer, COUNTER, "totalTime", session, timer.totalTime(getBaseTimeUnit()), timestamp);
        addDatapoint(timer, GAUGE, "avg", session, timer.mean(getBaseTimeUnit()), timestamp);
        addDatapoint(timer, GAUGE, "max", session, timer.max(getBaseTimeUnit()), timestamp);
    }

    private void addFunctionTimer(FunctionTimer timer, AggregateMetricSender.Session session, long timestamp) {
        addDatapoint(timer, COUNTER, "count", session, timer.count(), timestamp);
        addDatapoint(timer, COUNTER, "totalTime", session, timer.totalTime(getBaseTimeUnit()), timestamp);
        addDatapoint(timer, GAUGE, "avg", session, timer.mean(getBaseTimeUnit()), timestamp);
    }

    private void addDistributionSummary(DistributionSummary summary, AggregateMetricSender.Session session, long timestamp) {
        addDatapoint(summary, COUNTER, "count", session, summary.count(), timestamp);
        addDatapoint(summary, COUNTER, "totalTime", session, summary.totalAmount(), timestamp);
        addDatapoint(summary, GAUGE, "avg", session, summary.mean(), timestamp);
        addDatapoint(summary, GAUGE, "max", session, summary.max(), timestamp);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }
}
