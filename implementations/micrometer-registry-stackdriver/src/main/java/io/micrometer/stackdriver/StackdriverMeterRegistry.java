/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.stackdriver;

import com.google.api.Distribution;
import com.google.api.Metric;
import com.google.api.MetricDescriptor;
import com.google.api.MonitoredResource;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.monitoring.v3.*;
import com.google.protobuf.Timestamp;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepDistributionSummary;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepTimer;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

@Incubating(since = "1.1.0")
public class StackdriverMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("stackdriver-metrics-publisher");

    /**
     * The "global" type is meant as a catch-all when no other resource type is suitable, which
     * includes everything that Micrometer ships.
     * https://cloud.google.com/monitoring/custom-metrics/creating-metrics#which-resource
     */
    private static final String RESOURCE_TYPE = "global";
    private final Logger logger = LoggerFactory.getLogger(StackdriverMeterRegistry.class);
    private final StackdriverConfig config;
    /**
     * Metric names for which we have posted a custom metric
     */
    private final Set<String> verifiedDescriptors = ConcurrentHashMap.newKeySet();

    @Nullable
    private MetricServiceSettings metricServiceSettings;

    @Nullable
    private MetricServiceClient client;

    public StackdriverMeterRegistry(StackdriverConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, () -> MetricServiceSettings.newBuilder().build());
    }

    private StackdriverMeterRegistry(StackdriverConfig config, Clock clock, ThreadFactory threadFactory,
                                     Callable<MetricServiceSettings> metricServiceSettings) {
        super(config, clock);
        this.config = config;

        try {
            this.metricServiceSettings = metricServiceSettings.call();
        } catch (Exception e) {
            logger.error("unable to create stackdriver service settings", e);
        }

        config().namingConvention(new StackdriverNamingConvention());

        start(threadFactory);
    }

    public static Builder builder(StackdriverConfig config) {
        return new Builder(config);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            if (metricServiceSettings == null) {
                logger.error("unable to start stackdriver, service settings are not available");
            } else {
                try {
                    this.client = MetricServiceClient.create(metricServiceSettings);
                    logger.info("publishing metrics to stackdriver every " + TimeUtils.format(config.step()));
                    super.start(threadFactory);
                } catch (Exception e) {
                    logger.error("unable to create stackdriver client", e);
                }
            }
        }
    }

    @Override
    public void stop() {
        if (client != null)
            client.shutdownNow();
        super.stop();
    }

    @Override
    protected void publish() {
        if (client == null) {
            return;
        }

        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            Batch publishBatch = new Batch();

            Iterable<TimeSeries> series = batch.stream()
                    .flatMap(meter -> meter.match(
                            m -> createGauge(publishBatch, m),
                            m -> createCounter(publishBatch, m),
                            m -> createTimer(publishBatch, m),
                            m -> createSummary(publishBatch, m),
                            m -> createLongTaskTimer(publishBatch, m),
                            m -> createTimeGauge(publishBatch, m),
                            m -> createFunctionCounter(publishBatch, m),
                            m -> createFunctionTimer(publishBatch, m),
                            m -> createMeter(publishBatch, m)))
                    .collect(toList());

            try {
                CreateTimeSeriesRequest request = CreateTimeSeriesRequest.newBuilder()
                        .setName("projects/" + config.projectId())
                        .addAllTimeSeries(series)
                        .build();

                if (logger.isTraceEnabled()) {
                    logger.trace("publishing batch to stackdriver:\n{}", request);
                }

                client.createTimeSeries(request);
            } catch (ApiException e) {
                logger.warn("failed to send metrics to stackdriver: {}", e.getCause().getMessage());
            }
        }
    }

    private Stream<TimeSeries> createMeter(Batch batch, Meter m) {
        return stream(m.measure().spliterator(), false)
                .map(ms -> batch.createTimeSeries(m, ms.getValue(), ms.getStatistic().getTagValueRepresentation()));
    }

    private Stream<TimeSeries> createFunctionTimer(Batch batch, FunctionTimer timer) {
        long count = (long) timer.count();
        Distribution.Builder distribution = Distribution.newBuilder()
                .setMean(timer.mean(getBaseTimeUnit()))
                .setCount(count)
                .setBucketOptions(Distribution.BucketOptions.newBuilder()
                        .setExplicitBuckets(Distribution.BucketOptions.Explicit.newBuilder()
                                .addBounds(0.0)
                                .build()))
                .addBucketCounts(0)
                .addBucketCounts(count);

        return Stream.of(batch.createTimeSeries(timer, distribution.build()));
    }

    private Stream<TimeSeries> createFunctionCounter(Batch batch, FunctionCounter functionCounter) {
        return Stream.of(batch.createTimeSeries(functionCounter, functionCounter.count(), null));
    }

    private Stream<TimeSeries> createTimeGauge(Batch batch, TimeGauge timeGauge) {
        return Stream.of(batch.createTimeSeries(timeGauge, timeGauge.value(getBaseTimeUnit()), null));
    }

    private Stream<TimeSeries> createSummary(Batch batch, DistributionSummary summary) {
        return batch.createTimeSeries(summary, false);
    }

    private Stream<TimeSeries> createTimer(Batch batch, Timer timer) {
        return batch.createTimeSeries(timer, true);
    }

    private Stream<TimeSeries> createGauge(Batch batch, Gauge gauge) {
        return Stream.of(batch.createTimeSeries(gauge, gauge.value(), null));
    }

    private Stream<TimeSeries> createCounter(Batch batch, Counter counter) {
        return Stream.of(batch.createTimeSeries(counter, counter.count(), null));
    }

    private Stream<TimeSeries> createLongTaskTimer(Batch batch, LongTaskTimer longTaskTimer) {
        return Stream.of(
                batch.createTimeSeries(longTaskTimer, longTaskTimer.activeTasks(), "activeTasks"),
                batch.createTimeSeries(longTaskTimer, longTaskTimer.duration(getBaseTimeUnit()), "duration")
        );
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new StepDistributionSummary(id, clock, distributionStatisticConfig, scale,
                config.step().toMillis(), true);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        return new StepTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                this.config.step().toMillis(), true);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {
        private final StackdriverConfig config;
        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private Callable<MetricServiceSettings> metricServiceSettings = () -> MetricServiceSettings.newBuilder().build();

        Builder(StackdriverConfig config) {
            this.config = config;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder metricServiceSettings(Callable<MetricServiceSettings> metricServiceSettings) {
            this.metricServiceSettings = metricServiceSettings;
            return this;
        }

        public StackdriverMeterRegistry build() {
            return new StackdriverMeterRegistry(config, clock, threadFactory, metricServiceSettings);
        }
    }

    private class Batch {
        private final TimeInterval interval = TimeInterval.newBuilder()
                .setEndTime(Timestamp.newBuilder()
                        .setSeconds(clock.wallTime() / 1000)
                        .setNanos((int) (clock.wallTime() % 1000) * 1000000)
                        .build())
                .build();

        TimeSeries createTimeSeries(Meter meter, double value, @Nullable String statistic) {
            return createTimeSeries(meter.getId(), TypedValue.newBuilder().setDoubleValue(value).build(),
                    MetricDescriptor.ValueType.DOUBLE, statistic);
        }

        Stream<TimeSeries> createTimeSeries(HistogramSupport histogramSupport, boolean timeDomain) {
            HistogramSnapshot snapshot = histogramSupport.takeSnapshot();
            return Stream.concat(
                    Stream.of(
                            createTimeSeries(histogramSupport, distribution(snapshot, timeDomain)),
                            createTimeSeries(histogramSupport,
                                    timeDomain ? snapshot.max(getBaseTimeUnit()) : snapshot.max(),
                                    "max")
                    ),
                    Arrays.stream(snapshot.percentileValues())
                            .map(valueAtP -> createTimeSeries(histogramSupport,
                                    timeDomain ? valueAtP.value(getBaseTimeUnit()) : valueAtP.value(),
                                    "p" + DoubleFormat.decimalOrWhole(valueAtP.percentile() * 100)))
            );
        }

        TimeSeries createTimeSeries(Meter meter, Distribution distribution) {
            return createTimeSeries(meter.getId(),
                    TypedValue.newBuilder().setDistributionValue(distribution).build(),
                    MetricDescriptor.ValueType.DISTRIBUTION,
                    null);
        }

        private TimeSeries createTimeSeries(Meter.Id id, TypedValue typedValue, MetricDescriptor.ValueType valueType,
                                            @Nullable String statistic) {
            if (client != null)
                createMetricDescriptorIfNecessary(client, id, valueType, statistic);

            String metricType = metricType(id, statistic);

            Map<String, String> metricLabels = getConventionTags(id).stream()
                    .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

            return TimeSeries.newBuilder()
                    .setMetric(Metric.newBuilder()
                            .setType(metricType)
                            .putAllLabels(metricLabels)
                            .build())
                    .setResource(MonitoredResource.newBuilder()
                            .setType(RESOURCE_TYPE)
                            .putLabels("project_id", config.projectId())
                            .build())
                    .setMetricKind(MetricDescriptor.MetricKind.GAUGE) // https://cloud.google.com/monitoring/api/v3/metrics-details#metric-kinds
                    .setValueType(valueType)
                    .addPoints(Point.newBuilder()
                            .setInterval(interval)
                            .setValue(typedValue)
                            .build())
                    .build();
        }

        private void createMetricDescriptorIfNecessary(MetricServiceClient client, Meter.Id id,
                                                       MetricDescriptor.ValueType valueType, @Nullable String statistic) {
            if (!verifiedDescriptors.contains(id.getName())) {
                MetricDescriptor descriptor = MetricDescriptor.newBuilder()
                        .setType(metricType(id, statistic))
                        .setDescription(id.getDescription() == null ? "" : id.getDescription())
                        .setMetricKind(MetricDescriptor.MetricKind.GAUGE)
                        .setValueType(valueType)
                        .build();

                ProjectName name = ProjectName.of(config.projectId());

                CreateMetricDescriptorRequest request = CreateMetricDescriptorRequest.newBuilder()
                        .setName(name.toString())
                        .setMetricDescriptor(descriptor)
                        .build();

                if (logger.isTraceEnabled()) {
                    logger.trace("creating metric descriptor:\n{}", request);
                }

                try {
                    client.createMetricDescriptor(request);
                    verifiedDescriptors.add(id.getName());
                } catch (ApiException e) {
                    logger.warn("failed to create metric descriptor in stackdriver for meter " + id + " {}", e.getCause().getMessage());
                }
            }
        }

        private String metricType(Meter.Id id, @Nullable String statistic) {
            StringBuilder metricType = new StringBuilder("custom.googleapis.com/").append(getConventionName(id));
            if (statistic != null) {
                metricType.append("/").append(statistic);
            }
            return metricType.toString();
        }

        private Distribution distribution(HistogramSnapshot snapshot, boolean timeDomain) {
            CountAtBucket[] histogram = snapshot.histogramCounts();

            // selected finite buckets (represented as a normal histogram)
            AtomicLong truncatedSum = new AtomicLong();
            AtomicReference<Double> last = new AtomicReference<>(0.0);
            List<Long> bucketCounts = Arrays.stream(histogram)
                    .map(countAtBucket -> {
                        double cumulativeCount = countAtBucket.count();
                        long bucketCount = (long) (cumulativeCount - last.getAndSet(cumulativeCount));
                        truncatedSum.addAndGet(bucketCount);
                        return bucketCount;
                    })
                    .collect(toCollection(ArrayList::new));

            if (!bucketCounts.isEmpty()) {
                // trim zero-count buckets on the right side of the domain
                int lastNonzero = 0;
                for (int i = 0; i < bucketCounts.size(); i++) {
                    if (bucketCounts.get(i) > 0)
                        lastNonzero = i;
                }
                bucketCounts = bucketCounts.subList(0, lastNonzero + 1);
            }

            // add the "+infinity" bucket, which does NOT have a corresponding bucket boundary
            bucketCounts.add(snapshot.count() - truncatedSum.get());

            List<Double> bucketBoundaries = Arrays.stream(histogram)
                    .map(countAtBucket -> timeDomain ? countAtBucket.bucket(getBaseTimeUnit()) : countAtBucket.bucket())
                    .collect(toCollection(ArrayList::new));

            if (bucketBoundaries.size() != bucketCounts.size() - 1) {
                bucketBoundaries = bucketBoundaries.subList(0, bucketCounts.size() - 1);
            }

            // stackdriver requires at least one finite bucket
            if (bucketBoundaries.isEmpty()) {
                bucketBoundaries.add(0.0);
            }

            return Distribution.newBuilder()
                    .setMean(timeDomain ? snapshot.mean(getBaseTimeUnit()) : snapshot.mean())
                    .setCount(snapshot.count())
                    .setBucketOptions(Distribution.BucketOptions.newBuilder()
                            .setExplicitBuckets(Distribution.BucketOptions.Explicit.newBuilder()
                                    .addAllBounds(bucketBoundaries)
                                    .build())
                            .build())
                    .addAllBucketCounts(bucketCounts)
                    .build();
        }
    }
}
