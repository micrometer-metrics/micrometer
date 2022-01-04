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
package io.micrometer.cloudwatch;

import com.amazonaws.AbortedException;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.WarnThenDebugLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * @deprecated the micrometer-registry-cloudwatch implementation has been deprecated in favour of
 *             micrometer-registry-cloudwatch2, which uses AWS SDK for Java 2.x
 */
@Deprecated
public class CloudWatchMeterRegistry extends StepMeterRegistry {

    private static final Map<String, StandardUnit> STANDARD_UNIT_BY_LOWERCASE_VALUE;

    static {
        Map<String, StandardUnit> standardUnitByLowercaseValue = new HashMap<>();
        for (StandardUnit standardUnit : StandardUnit.values()) {
            standardUnitByLowercaseValue.put(standardUnit.toString().toLowerCase(), standardUnit);
        }
        STANDARD_UNIT_BY_LOWERCASE_VALUE = Collections.unmodifiableMap(standardUnitByLowercaseValue);
    }

    private final CloudWatchConfig config;
    private final AmazonCloudWatchAsync amazonCloudWatchAsync;
    private final Logger logger = LoggerFactory.getLogger(CloudWatchMeterRegistry.class);
    private static final WarnThenDebugLogger warnThenDebugLogger = new WarnThenDebugLogger(CloudWatchMeterRegistry.class);

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   AmazonCloudWatchAsync amazonCloudWatchAsync) {
        this(config, clock, amazonCloudWatchAsync, new NamedThreadFactory("cloudwatch-metrics-publisher"));
    }

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   AmazonCloudWatchAsync amazonCloudWatchAsync, ThreadFactory threadFactory) {
        super(config, clock);

        if (config.namespace() == null) {
            throw new io.micrometer.core.instrument.config.MissingRequiredConfigurationException(
                    "namespace must be set to report metrics to CloudWatch");
        }

        this.amazonCloudWatchAsync = amazonCloudWatchAsync;
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

    // VisibleForTesting
    void sendMetricData(List<MetricDatum> metricData) throws InterruptedException {
        PutMetricDataRequest putMetricDataRequest = new PutMetricDataRequest()
                .withNamespace(config.namespace())
                .withMetricData(metricData);
        CountDownLatch latch = new CountDownLatch(1);
        amazonCloudWatchAsync.putMetricDataAsync(putMetricDataRequest, new AsyncHandler<PutMetricDataRequest, PutMetricDataResult>() {
            @Override
            public void onError(Exception exception) {
                if (exception instanceof AbortedException) {
                    logger.warn("sending metric data was aborted: {}", exception.getMessage());
                } else {
                    logger.error("error sending metric data.", exception);
                }
                latch.countDown();
            }

            @Override
            public void onSuccess(PutMetricDataRequest request, PutMetricDataResult result) {
                logger.debug("published metric with namespace:{}", request.getNamespace());
                latch.countDown();
            }
        });
        try {
            @SuppressWarnings("deprecation")
            long readTimeoutMillis = config.readTimeout().toMillis();
            latch.await(readTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("metrics push to cloudwatch took longer than expected");
            throw e;
        }
    }

    //VisibleForTesting
    List<MetricDatum> metricData() {
        Batch batch = new Batch();
        return getMeters().stream().flatMap(m -> m.match(
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
        private final Date timestamp = new Date(clock.wallTime());

        private Stream<MetricDatum> gaugeData(Gauge gauge) {
            MetricDatum metricDatum = metricDatum(gauge.getId(), "value", gauge.value());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        private Stream<MetricDatum> counterData(Counter counter) {
            return Stream.of(metricDatum(counter.getId(), "count", StandardUnit.Count, counter.count()));
        }

        // VisibleForTesting
        Stream<MetricDatum> timerData(Timer timer) {
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            metrics.add(metricDatum(timer.getId(), "sum", getBaseTimeUnit().name(), timer.totalTime(getBaseTimeUnit())));
            long count = timer.count();
            metrics.add(metricDatum(timer.getId(), "count", StandardUnit.Count, count));
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
            metrics.add(metricDatum(summary.getId(), "count", StandardUnit.Count, count));
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
            MetricDatum metricDatum = metricDatum(counter.getId(), "count", StandardUnit.Count, counter.count());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        // VisibleForTesting
        Stream<MetricDatum> functionTimerData(FunctionTimer timer) {
            // we can't know anything about max and percentiles originating from a function timer
            double sum = timer.totalTime(getBaseTimeUnit());
            if (!Double.isFinite(sum)) {
                return Stream.empty();
            }
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            double count = timer.count();
            metrics.add(metricDatum(timer.getId(), "count", StandardUnit.Count, count));
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
            return new MetricDatum()
                    .withMetricName(getMetricName(id, suffix))
                    .withDimensions(toDimensions(tags))
                    .withTimestamp(timestamp)
                    .withValue(CloudWatchUtils.clampMetricValue(value))
                    .withUnit(standardUnit);
        }

        // VisibleForTesting
        String getMetricName(Meter.Id id, @Nullable String suffix) {
            String name = suffix != null ? id.getName() + "." + suffix : id.getName();
            return config().namingConvention().name(name, id.getType(), id.getBaseUnit());
        }

        private StandardUnit toStandardUnit(@Nullable String unit) {
            if (unit == null) {
                return StandardUnit.None;
            }
            StandardUnit standardUnit = STANDARD_UNIT_BY_LOWERCASE_VALUE.get(unit.toLowerCase());
            return standardUnit != null ? standardUnit : StandardUnit.None;
        }

        private List<Dimension> toDimensions(List<Tag> tags) {
            return tags.stream()
                    .filter(this::isAcceptableTag)
                    .map(tag -> new Dimension().withName(tag.getKey()).withValue(tag.getValue()))
                    .collect(toList());
        }

        private boolean isAcceptableTag(Tag tag) {
            if (StringUtils.isBlank(tag.getValue())) {
                warnThenDebugLogger.log("Dropping a tag with key '" + tag.getKey() + "' because its value is blank.");
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
