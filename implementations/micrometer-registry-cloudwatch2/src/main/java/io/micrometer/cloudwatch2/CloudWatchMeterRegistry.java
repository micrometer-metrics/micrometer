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
package io.micrometer.cloudwatch2;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.time.Instant;
import java.util.*;
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
 * @author Jonatan Ivanov
 * @since 1.2.0
 */
public class CloudWatchMeterRegistry extends StepMeterRegistry {

    private static final Map<String, StandardUnit> STANDARD_UNIT_BY_LOWERCASE_VALUE;

    // https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_Dimension.html
    private static final int MAX_DIMENSIONS_SIZE = 30;

    static {
        Map<String, StandardUnit> standardUnitByLowercaseValue = new HashMap<>();
        for (StandardUnit standardUnit : StandardUnit.values()) {
            if (standardUnit != StandardUnit.UNKNOWN_TO_SDK_VERSION) {
                standardUnitByLowercaseValue.put(standardUnit.toString().toLowerCase(Locale.ROOT), standardUnit);
            }
        }
        STANDARD_UNIT_BY_LOWERCASE_VALUE = Collections.unmodifiableMap(standardUnitByLowercaseValue);
    }

    private final CloudWatchConfig config;

    private final CloudWatchAsyncClient cloudWatchAsyncClient;

    private final Logger logger = LoggerFactory.getLogger(CloudWatchMeterRegistry.class);

    private static final WarnThenDebugLogger blankTagValueLogger = new WarnThenDebugLogger(
            CloudWatchMeterRegistry.class);

    private static final WarnThenDebugLogger tooManyTagsLogger = new WarnThenDebugLogger(CloudWatchMeterRegistry.class);

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock, CloudWatchAsyncClient cloudWatchAsyncClient) {
        this(config, clock, cloudWatchAsyncClient, new NamedThreadFactory("cloudwatch-metrics-publisher"));
    }

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock, CloudWatchAsyncClient cloudWatchAsyncClient,
            ThreadFactory threadFactory) {
        super(config, clock);
        this.cloudWatchAsyncClient = cloudWatchAsyncClient;
        this.config = config;

        config().namingConvention(new CloudWatchNamingConvention());
        start(threadFactory);
    }

    @Override
    protected void publish() {
        boolean interrupted = false;
        try {
            for (List<MetricDatum> batch : MetricDatumPartition.partition(metricData(), config.batchSize())) {
                try {
                    sendMetricData(batch);
                }
                catch (InterruptedException ex) {
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

    // VisibleForTesting
    void sendMetricData(List<MetricDatum> metricData) throws InterruptedException {
        PutMetricDataRequest putMetricDataRequest = PutMetricDataRequest.builder()
            .namespace(config.namespace())
            .metricData(metricData)
            .build();
        CountDownLatch latch = new CountDownLatch(1);
        cloudWatchAsyncClient.putMetricData(putMetricDataRequest).whenComplete((response, t) -> {
            if (t != null) {
                if (t instanceof AbortedException) {
                    logger.warn("sending metric data was aborted: {}", t.getMessage());
                }
                else {
                    logger.error("error sending metric data.", t);
                }
                logger.debug("failed PutMetricDataRequest: {}", putMetricDataRequest);
            }
            else {
                logger.debug("published {} metrics with namespace:{}", metricData.size(),
                        putMetricDataRequest.namespace());
            }
            latch.countDown();
        });
        try {
            @SuppressWarnings("deprecation")
            long readTimeoutMillis = config.readTimeout().toMillis();
            boolean awaitSuccess = latch.await(readTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!awaitSuccess) {
                logger.warn("metrics push to cloudwatch took longer than expected");
            }
        }
        catch (InterruptedException e) {
            logger.warn("interrupted during sending metric data");
            throw e;
        }
    }

    // VisibleForTesting
    List<MetricDatum> metricData() {
        Batch batch = new Batch();
        // @formatter:off
        return getMeters().stream()
            .flatMap(m -> m.match(
                    batch::gaugeData,
                    batch::counterData,
                    batch::timerData,
                    batch::summaryData,
                    batch::longTaskTimerData,
                    batch::timeGaugeData,
                    batch::functionCounterData,
                    batch::functionTimerData,
                    batch::metricData))
            .collect(toList());
        // @formatter:on
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
            return Stream.of(metricDatum(counter.getId(), "count", StandardUnit.COUNT, counter.count()));
        }

        // VisibleForTesting
        Stream<MetricDatum> timerData(Timer timer) {
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            metrics
                .add(metricDatum(timer.getId(), "sum", getBaseTimeUnit().name(), timer.totalTime(getBaseTimeUnit())));
            long count = timer.count();
            metrics.add(metricDatum(timer.getId(), "count", StandardUnit.COUNT, count));
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
            metrics.add(metricDatum(summary.getId(), "count", StandardUnit.COUNT, count));
            if (count > 0) {
                metrics.add(metricDatum(summary.getId(), "avg", summary.mean()));
                metrics.add(metricDatum(summary.getId(), "max", summary.max()));
            }
            return metrics.build();
        }

        private Stream<MetricDatum> longTaskTimerData(LongTaskTimer longTaskTimer) {
            return Stream.of(metricDatum(longTaskTimer.getId(), "activeTasks", longTaskTimer.activeTasks()),
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
            MetricDatum metricDatum = metricDatum(counter.getId(), "count", StandardUnit.COUNT, counter.count());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        // VisibleForTesting
        Stream<MetricDatum> functionTimerData(FunctionTimer timer) {
            // we can't know anything about max and percentiles originating from a
            // function timer
            double sum = timer.totalTime(getBaseTimeUnit());
            if (!Double.isFinite(sum)) {
                return Stream.empty();
            }
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            double count = timer.count();
            metrics.add(metricDatum(timer.getId(), "count", StandardUnit.COUNT, count));
            metrics.add(metricDatum(timer.getId(), "sum", sum));
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
            return metricDatum(id, null, id.getBaseUnit(), value);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, double value) {
            return metricDatum(id, suffix, id.getBaseUnit(), value);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, @Nullable String unit, double value) {
            return metricDatum(id, suffix, toStandardUnit(unit), value);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, StandardUnit standardUnit, double value) {
            if (Double.isNaN(value)) {
                return null;
            }

            List<Tag> tags = id.getConventionTags(config().namingConvention());
            if (tags.size() > MAX_DIMENSIONS_SIZE) {
                tooManyTagsLogger.log(() -> "Meter " + id.getName() + " has more tags (" + tags.size()
                        + ") than the max supported by CloudWatch (" + MAX_DIMENSIONS_SIZE
                        + "). Some tags will be dropped.");
            }
            return MetricDatum.builder()
                .storageResolution(config.highResolution() ? 1 : 60)
                .metricName(getMetricName(id, suffix))
                .dimensions(toDimensions(tags))
                .timestamp(timestamp)
                .value(CloudWatchUtils.clampMetricValue(value))
                .unit(standardUnit)
                .build();
        }

        // VisibleForTesting
        String getMetricName(Meter.Id id, @Nullable String suffix) {
            String name = suffix != null ? id.getName() + "." + suffix : id.getName();
            return config().namingConvention().name(name, id.getType(), id.getBaseUnit());
        }

        // VisibleForTesting
        StandardUnit toStandardUnit(@Nullable String unit) {
            if (unit == null) {
                return StandardUnit.NONE;
            }
            StandardUnit standardUnit = STANDARD_UNIT_BY_LOWERCASE_VALUE.get(unit.toLowerCase(Locale.ROOT));
            return standardUnit != null ? standardUnit : StandardUnit.NONE;
        }

        private List<Dimension> toDimensions(List<Tag> tags) {
            return tags.stream()
                .filter(this::isAcceptableTag)
                .limit(MAX_DIMENSIONS_SIZE)
                .map(tag -> Dimension.builder().name(tag.getKey()).value(tag.getValue()).build())
                .collect(toList());
        }

        private boolean isAcceptableTag(Tag tag) {
            if (StringUtils.isBlank(tag.getValue())) {
                blankTagValueLogger
                    .log(() -> "Dropping a tag with key '" + tag.getKey() + "' because its value is blank.");
                return false;
            }
            return true;
        }

    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

}
