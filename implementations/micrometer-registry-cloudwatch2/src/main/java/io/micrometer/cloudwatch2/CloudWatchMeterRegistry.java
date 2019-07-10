/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link StepMeterRegistry} for Amazon CloudWatch.
 *
 * @author Dawid Kublik
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Pierre-Yves B.
 */
public class CloudWatchMeterRegistry extends StepMeterRegistry {
    private final CloudWatchConfig config;
    private final CloudWatchAsyncClient cloudWatchAsyncClient;
    private final Logger logger = LoggerFactory.getLogger(CloudWatchMeterRegistry.class);

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   CloudWatchAsyncClient cloudWatchAsyncClient) {
        this(config, clock, cloudWatchAsyncClient, new NamedThreadFactory("cloudwatch-metrics-publisher"));
    }

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   CloudWatchAsyncClient cloudWatchAsyncClient, ThreadFactory threadFactory) {
        super(config, clock);

        if (config.namespace() == null) {
            throw new MissingRequiredConfigurationException("namespace must be set to report metrics to CloudWatch");
        }

        this.cloudWatchAsyncClient = cloudWatchAsyncClient;
        this.config = config;
        config().namingConvention(NamingConvention.identity);
        start(threadFactory);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to cloudwatch every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        boolean interrupted = false;
        try {
            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                try {
                    sendMetricData(metricData(batch));
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
        }
        finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void sendMetricData(List<MetricDatum> metricData) throws InterruptedException {
        PutMetricDataRequest putMetricDataRequest = PutMetricDataRequest.builder()
                .namespace(config.namespace())
                .metricData(metricData)
                .build();
        CountDownLatch latch = new CountDownLatch(1);
        cloudWatchAsyncClient.putMetricData(putMetricDataRequest).whenCompleteAsync((response, t) -> {
            if (t != null) {
                if (t instanceof AbortedException) {
                    logger.warn("sending metric data was aborted: {}", t.getMessage());
                } else {
                    logger.error("error sending metric data.", t);
                }
            } else {
                logger.debug("published metric with namespace:{}", putMetricDataRequest.namespace());
            }
            latch.countDown();
        });
        try {
            latch.await(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("metrics push to cloudwatch took longer than expected");
            throw e;
        }
    }

    //VisibleForTesting
    List<MetricDatum> metricData(List<Meter> meters) {
        Batch batch = new Batch();
        return meters.stream().flatMap(m -> m.match(
                batch::gaugeData,
                batch::counterData,
                batch::timerData,
                batch::summaryData,
                batch::longTaskTimerData,
                batch::timeGaugeData,
                batch::functionCounterData,
                batch::functionTimerData,
                batch::metricData)
        ).collect(toList());
    }

    // VisibleForTesting
    class Batch {
        private final Instant timestamp = Instant.ofEpochMilli(clock.wallTime());

        private Stream<MetricDatum> gaugeData(Gauge gauge) {
            MetricDatum metricDatum = metricDatum(gauge.getId(), "value", gauge.value());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        private Stream<MetricDatum> counterData(Counter counter) {
            return Stream.of(metricDatum(counter.getId(), "count", "count", counter.count()));
        }

        // VisibleForTesting
        Stream<MetricDatum> timerData(Timer timer) {
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            metrics.add(metricDatum(timer.getId(), "sum", getBaseTimeUnit().name(), timer.totalTime(getBaseTimeUnit())));
            long count = timer.count();
            metrics.add(metricDatum(timer.getId(), "count", "count", count));
            if (count > 0) {
                metrics.add(metricDatum(timer.getId(), "avg", getBaseTimeUnit().name(), timer.mean(getBaseTimeUnit())));
                metrics.add(metricDatum(timer.getId(), "max", getBaseTimeUnit().name(), timer.max(getBaseTimeUnit())));
            }
            return metrics.build();
        }

        // VisibleForTesting
        Stream<MetricDatum> summaryData(DistributionSummary summary) {
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            metrics.add(metricDatum(summary.getId(), "sum", summary.totalAmount()));
            long count = summary.count();
            metrics.add(metricDatum(summary.getId(), "count", "count", count));
            if (count > 0) {
                metrics.add(metricDatum(summary.getId(), "avg", summary.mean()));
                metrics.add(metricDatum(summary.getId(), "max", summary.max()));
            }
            return metrics.build();
        }

        private Stream<MetricDatum> longTaskTimerData(LongTaskTimer longTaskTimer) {
            return Stream.of(
                    metricDatum(longTaskTimer.getId(), "activeTasks", longTaskTimer.activeTasks()),
                    metricDatum(longTaskTimer.getId(), "duration", longTaskTimer.duration(getBaseTimeUnit())));
        }

        private Stream<MetricDatum> timeGaugeData(TimeGauge gauge) {
            MetricDatum metricDatum = metricDatum(gauge.getId(), "value", gauge.value(getBaseTimeUnit()));
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        // VisibleForTesting
        Stream<MetricDatum> functionCounterData(FunctionCounter counter) {
            MetricDatum metricDatum = metricDatum(counter.getId(), "count", "count", counter.count());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        // VisibleForTesting
        Stream<MetricDatum> functionTimerData(FunctionTimer timer) {
            // we can't know anything about max and percentiles originating from a function timer
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            double count = timer.count();
            metrics.add(metricDatum(timer.getId(), "count", "count", count));
            if (count > 0) {
                metrics.add(metricDatum(timer.getId(), "avg", timer.mean(getBaseTimeUnit())));
            }
            return metrics.build();
        }

        // VisibleForTesting
        Stream<MetricDatum> metricData(Meter m) {
            return stream(m.measure().spliterator(), false)
                    .map(ms -> metricDatum(m.getId().withTag(ms.getStatistic()), ms.getValue()))
                    .filter(Objects::nonNull);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, double value) {
            return metricDatum(id, null, null, value);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, double value) {
            return metricDatum(id, suffix, null, value);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, @Nullable String unit, double value) {
            if (Double.isNaN(value)) {
                return null;
            }

            List<Tag> tags = id.getConventionTags(config().namingConvention());
            return MetricDatum.builder()
                    .metricName(getMetricName(id, suffix))
                    .dimensions(toDimensions(tags))
                    .timestamp(timestamp)
                    .value(CloudWatchUtils.clampMetricValue(value))
                    .unit(toStandardUnit(unit))
                    .build();
        }

        // VisibleForTesting
        String getMetricName(Meter.Id id, @Nullable String suffix) {
            String name = suffix != null ? id.getName() + "." + suffix : id.getName();
            return config().namingConvention().name(name, id.getType(), id.getBaseUnit());
        }

        private StandardUnit toStandardUnit(@Nullable String unit) {
            if (unit == null) {
                return StandardUnit.NONE;
            }
            switch (unit.toLowerCase()) {
                case "bytes":
                    return StandardUnit.BYTES;
                case "milliseconds":
                    return StandardUnit.MILLISECONDS;
                case "count":
                    return StandardUnit.COUNT;
                default:
                    return StandardUnit.NONE;
            }
        }


        private List<Dimension> toDimensions(List<Tag> tags) {
            return tags.stream()
                    .map(tag -> Dimension.builder().name(tag.getKey()).value(tag.getValue()).build())
                    .collect(toList());
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
