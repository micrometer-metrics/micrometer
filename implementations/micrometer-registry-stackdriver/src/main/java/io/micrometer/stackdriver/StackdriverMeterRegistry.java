/*
 * Copyright 2018 VMware, Inc.
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
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.api.MetricDescriptor.MetricKind.CUMULATIVE;
import static com.google.api.MetricDescriptor.MetricKind.GAUGE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link StepMeterRegistry} for Stackdriver.
 *
 * @author Jon Schneider
 * @since 1.1.0
 */
public class StackdriverMeterRegistry extends StepMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("stackdriver-metrics-publisher");

    /**
     * Stackdriver's API only allows up to 200 TimeSeries per request
     * https://cloud.google.com/monitoring/quotas#custom_metrics_quotas
     */
    private static final int TIMESERIES_PER_REQUEST_LIMIT = 200;

    private final Logger logger = LoggerFactory.getLogger(StackdriverMeterRegistry.class);

    private final StackdriverConfig config;

    private long previousBatchEndTime;

    /**
     * Metric names for which we have posted a custom metric
     */
    private final Set<String> verifiedDescriptors = ConcurrentHashMap.newKeySet();

    @Nullable
    private MetricServiceSettings metricServiceSettings;

    // VisibleForTesting
    @Nullable
    MetricServiceClient client;

    public StackdriverMeterRegistry(StackdriverConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, () -> MetricServiceSettings.newBuilder().build());
    }

    private StackdriverMeterRegistry(StackdriverConfig config, Clock clock, ThreadFactory threadFactory,
            Callable<MetricServiceSettings> metricServiceSettings) {
        super(config, clock);

        this.config = config;

        try {
            this.metricServiceSettings = metricServiceSettings.call();
        }
        catch (Exception e) {
            logger.error("unable to create stackdriver service settings", e);
        }

        config().namingConvention(new StackdriverNamingConvention());

        previousBatchEndTime = clock.wallTime();

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
            }
            else {
                shutdownClientIfNecessary(true);
                try {
                    this.client = MetricServiceClient.create(metricServiceSettings);
                    super.start(threadFactory);
                }
                catch (Exception e) {
                    logger.error("unable to create stackdriver client", e);
                }
            }
        }
    }

    @Override
    public void close() {
        try {
            super.close();
        }
        finally {
            shutdownClientIfNecessary(false);
        }
    }

    @Override
    public void stop() {
        super.stop();
    }

    private void shutdownClientIfNecessary(boolean quietly) {
        if (client == null)
            return;
        if (!client.isShutdown()) {
            try {
                client.shutdownNow();
                boolean terminated = client.awaitTermination(10, TimeUnit.SECONDS);
                if (!terminated) {
                    logger.warn("The metric service client failed to terminate within the timeout");
                }
            }
            catch (RuntimeException e) {
                if (quietly) {
                    logger.warn("Failed to shutdown the metric service client", e);
                }
                else {
                    throw e;
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        try {
            client.close();
        }
        catch (RuntimeException e) {
            if (quietly) {
                logger.warn("Failed to close metric service client", e);
            }
            else {
                throw e;
            }
        }
        client = null;
    }

    @Override
    protected void publish() {
        if (client == null) {
            return;
        }

        Batch publishBatch = new Batch();

        AtomicLong partitioningCounter = new AtomicLong();
        long partitionSize = Math.min(config.batchSize(), TIMESERIES_PER_REQUEST_LIMIT);

        Collection<List<TimeSeries>> series = getMeters().stream()
            .flatMap(meter -> meter.match(m -> createGauge(publishBatch, m), m -> createCounter(publishBatch, m),
                    m -> createTimer(publishBatch, m), m -> createSummary(publishBatch, m),
                    m -> createLongTaskTimer(publishBatch, m), m -> createTimeGauge(publishBatch, m),
                    m -> createFunctionCounter(publishBatch, m), m -> createFunctionTimer(publishBatch, m),
                    m -> createMeter(publishBatch, m)))
            .collect(groupingBy(o -> partitioningCounter.incrementAndGet() / partitionSize))
            .values();

        for (List<TimeSeries> partition : series) {
            try {
                CreateTimeSeriesRequest request = CreateTimeSeriesRequest.newBuilder()
                    .setName("projects/" + config.projectId())
                    .addAllTimeSeries(partition)
                    .build();

                logger.trace("publishing batch to Stackdriver:{}{}", System.lineSeparator(), request);

                client.createTimeSeries(request);
                logger.debug("successfully sent {} TimeSeries to Stackdriver", partition.size());
            }
            catch (ApiException e) {
                logger.warn("failed to send metrics to Stackdriver", e);
            }
        }
    }

    // VisibleForTesting
    Stream<TimeSeries> createMeter(Batch batch, Meter m) {
        return stream(m.measure().spliterator(), false)
            .map(ms -> batch.createTimeSeries(m, ms.getValue(), ms.getStatistic().getTagValueRepresentation(), GAUGE));
    }

    // VisibleForTesting
    Stream<TimeSeries> createFunctionTimer(Batch batch, FunctionTimer timer) {
        long count = (long) timer.count();
        Distribution.Builder distribution = Distribution.newBuilder()
            .setMean(timer.mean(getBaseTimeUnit()))
            .setCount(count)
            .setBucketOptions(Distribution.BucketOptions.newBuilder()
                .setExplicitBuckets(Distribution.BucketOptions.Explicit.newBuilder().addBounds(0.0).build()))
            .addBucketCounts(0)
            .addBucketCounts(count);

        return Stream.of(batch.createTimeSeries(timer, distribution.build(), null, GAUGE));
    }

    // VisibleForTesting
    Stream<TimeSeries> createFunctionCounter(Batch batch, FunctionCounter functionCounter) {
        return Stream.of(batch.createTimeSeries(functionCounter, functionCounter.count(), null,
                config.useSemanticMetricTypes() ? CUMULATIVE : GAUGE));
    }

    // VisibleForTesting
    Stream<TimeSeries> createTimeGauge(Batch batch, TimeGauge timeGauge) {
        return Stream.of(batch.createTimeSeries(timeGauge, timeGauge.value(getBaseTimeUnit()), null, GAUGE));
    }

    // VisibleForTesting
    Stream<TimeSeries> createSummary(Batch batch, DistributionSummary summary) {
        return batch.createTimeSeries(summary, false);
    }

    // VisibleForTesting
    Stream<TimeSeries> createTimer(Batch batch, Timer timer) {
        return batch.createTimeSeries(timer, true);
    }

    // VisibleForTesting
    Stream<TimeSeries> createGauge(Batch batch, Gauge gauge) {
        return Stream.of(batch.createTimeSeries(gauge, gauge.value(), null, GAUGE));
    }

    // VisibleForTesting
    Stream<TimeSeries> createCounter(Batch batch, Counter counter) {
        return Stream.of(batch.createTimeSeries(counter, counter.count(), null,
                config.useSemanticMetricTypes() ? CUMULATIVE : GAUGE));
    }

    // VisibleForTesting
    Stream<TimeSeries> createLongTaskTimer(Batch batch, LongTaskTimer longTaskTimer) {
        return Stream.of(batch.createTimeSeries(longTaskTimer, longTaskTimer.activeTasks(), "activeTasks", GAUGE),
                batch.createTimeSeries(longTaskTimer, longTaskTimer.duration(getBaseTimeUnit()), "duration", GAUGE));
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new StackdriverDistributionSummary(id, clock, distributionStatisticConfig, scale,
                config.step().toMillis());
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        return new StackdriverTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                this.config.step().toMillis());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {

        private final StackdriverConfig config;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        private Callable<MetricServiceSettings> metricServiceSettings;

        Builder(StackdriverConfig config) {
            this.config = config;
            this.metricServiceSettings = () -> {
                MetricServiceSettings.Builder builder = MetricServiceSettings.newBuilder();
                if (config.credentials() != null) {
                    builder.setCredentialsProvider(config.credentials());
                }
                builder.setHeaderProvider(new UserAgentHeaderProvider("stackdriver"));
                return builder.build();
            };
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

    private Timestamp buildTimestamp(long timeMs) {
        return Timestamp.newBuilder().setSeconds(timeMs / 1000).setNanos((int) (timeMs % 1000) * 1000000).build();
    }

    // VisibleForTesting
    class Batch {

        private final Timestamp startTime;

        private final Timestamp endTime;

        Batch() {
            long wallTime = clock.wallTime();
            startTime = buildTimestamp(previousBatchEndTime + 1);
            endTime = buildTimestamp(wallTime);
            previousBatchEndTime = wallTime;
        }

        TimeSeries createTimeSeries(Meter meter, double value, @Nullable String statistic,
                MetricDescriptor.MetricKind metricKind) {
            return createTimeSeries(meter, TypedValue.newBuilder().setDoubleValue(value).build(),
                    MetricDescriptor.ValueType.DOUBLE, statistic, metricKind);
        }

        TimeSeries createTimeSeries(Meter meter, long value, @Nullable String statistic,
                MetricDescriptor.MetricKind metricKind) {
            return createTimeSeries(meter, TypedValue.newBuilder().setInt64Value(value).build(),
                    MetricDescriptor.ValueType.INT64, statistic, metricKind);
        }

        TimeSeries createTimeSeries(Meter meter, Distribution distribution, @Nullable String statistic,
                MetricDescriptor.MetricKind metricKind) {
            return createTimeSeries(meter, TypedValue.newBuilder().setDistributionValue(distribution).build(),
                    MetricDescriptor.ValueType.DISTRIBUTION, statistic, metricKind);
        }

        Stream<TimeSeries> createTimeSeries(HistogramSupport histogramSupport, boolean timeDomain) {
            HistogramSnapshot snapshot = histogramSupport.takeSnapshot();
            return Stream.concat(
                    Stream.of(createTimeSeries(histogramSupport, distribution(snapshot, timeDomain), null, GAUGE),
                            createTimeSeries(histogramSupport,
                                    timeDomain ? snapshot.max(getBaseTimeUnit()) : snapshot.max(), "max", GAUGE),
                            createTimeSeries(histogramSupport, snapshot.count(), "count",
                                    config.useSemanticMetricTypes() ? CUMULATIVE : GAUGE)),
                    Arrays.stream(snapshot.percentileValues())
                        .map(valueAtP -> createTimeSeries(histogramSupport,
                                timeDomain ? valueAtP.value(getBaseTimeUnit()) : valueAtP.value(),
                                "p" + DoubleFormat.wholeOrDecimal(valueAtP.percentile() * 100), GAUGE)));
        }

        private TimeSeries createTimeSeries(Meter meter, TypedValue typedValue, MetricDescriptor.ValueType valueType,
                @Nullable String statistic, MetricDescriptor.MetricKind metricKind) {
            Meter.Id id = meter.getId();
            if (client != null)
                createMetricDescriptorIfNecessary(client, id, valueType, statistic, metricKind);

            String metricType = metricType(id, statistic);

            Map<String, String> metricLabels = getConventionTags(id).stream()
                .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

            return TimeSeries.newBuilder()
                .setMetric(Metric.newBuilder().setType(metricType).putAllLabels(metricLabels).build())
                .setResource(MonitoredResource.newBuilder()
                    .setType(config.resourceType())
                    .putLabels("project_id", config.projectId())
                    .putAllLabels(config.resourceLabels())
                    .build())
                .setMetricKind(metricKind) // https://cloud.google.com/monitoring/api/v3/metrics-details#metric-kinds
                .setValueType(valueType)
                .addPoints(Point.newBuilder().setInterval(interval(metricKind)).setValue(typedValue).build())
                .build();
        }

        private void createMetricDescriptorIfNecessary(MetricServiceClient client, Meter.Id id,
                MetricDescriptor.ValueType valueType, @Nullable String statistic,
                MetricDescriptor.MetricKind metricKind) {

            if (verifiedDescriptors.isEmpty()) {
                prePopulateVerifiedDescriptors();
            }

            final String metricType = metricType(id, statistic);
            if (!verifiedDescriptors.contains(metricType)) {
                MetricDescriptor descriptor = MetricDescriptor.newBuilder()
                    .setType(metricType)
                    .setDescription(id.getDescription() == null ? "" : id.getDescription())
                    .setMetricKind(metricKind)
                    .setValueType(valueType)
                    .build();

                ProjectName name = ProjectName.of(config.projectId());

                CreateMetricDescriptorRequest request = CreateMetricDescriptorRequest.newBuilder()
                    .setName(name.toString())
                    .setMetricDescriptor(descriptor)
                    .build();

                logger.trace("creating metric descriptor:{}{}", System.lineSeparator(), request);

                try {
                    client.createMetricDescriptor(request);
                    verifiedDescriptors.add(metricType);
                }
                catch (ApiException e) {
                    logger.warn("failed to create metric descriptor in Stackdriver for meter " + id, e);
                }
            }
        }

        private void prePopulateVerifiedDescriptors() {
            try {
                if (client != null) {
                    final String prefix = metricType(new Meter.Id("", Tags.empty(), null, null, Meter.Type.OTHER),
                            null);
                    final String filter = String.format("metric.type = starts_with(\"%s\")", prefix);
                    final String projectName = "projects/" + config.projectId();

                    final ListMetricDescriptorsRequest listMetricDescriptorsRequest = ListMetricDescriptorsRequest
                        .newBuilder()
                        .setName(projectName)
                        .setFilter(filter)
                        .build();

                    final MetricServiceClient.ListMetricDescriptorsPagedResponse listMetricDescriptorsPagedResponse = client
                        .listMetricDescriptors(listMetricDescriptorsRequest);
                    listMetricDescriptorsPagedResponse.iterateAll()
                        .forEach(metricDescriptor -> verifiedDescriptors.add(metricDescriptor.getType()));

                    logger.trace(
                            "Pre populated verified descriptors for project: {}, with filter: {}, existing metrics: {}",
                            projectName, filter, verifiedDescriptors);
                }
            }
            catch (Exception e) {
                // only log on warning and continue, this should not be a showstopper
                logger.warn("Failed to pre populate verified descriptors for {}", config.projectId(), e);
            }
        }

        private String metricType(Meter.Id id, @Nullable String statistic) {
            StringBuilder metricType = new StringBuilder(config.metricTypePrefix()).append(getConventionName(id));
            if (statistic != null) {
                metricType.append('/').append(statistic);
            }
            return metricType.toString();
        }

        private TimeInterval interval(MetricDescriptor.MetricKind metricKind) {
            TimeInterval.Builder builder = TimeInterval.newBuilder().setEndTime(endTime);
            if (metricKind == CUMULATIVE) {
                builder.setStartTime(startTime);
            }
            return builder.build();
        }

        // VisibleForTesting
        Distribution distribution(HistogramSnapshot snapshot, boolean timeDomain) {
            CountAtBucket[] histogram = snapshot.histogramCounts();

            List<Long> bucketCounts = Arrays.stream(histogram)
                .map(CountAtBucket::count)
                .map(Double::longValue)
                .collect(toCollection(ArrayList::new));
            long cumulativeCount = Arrays.stream(histogram).mapToLong(c -> (long) c.count()).sum();

            // no-op histogram will have no buckets; other histograms should have at least
            // the +Inf bucket
            if (!bucketCounts.isEmpty() && bucketCounts.size() > 1) {
                // the rightmost bucket should be the infinity bucket; do not trim that
                int endIndex = bucketCounts.size() - 2;
                // trim zero-count buckets on the right side of the domain
                if (bucketCounts.get(endIndex) == 0) {
                    int lastNonZeroIndex = 0;
                    for (int i = endIndex - 1; i >= 0; i--) {
                        if (bucketCounts.get(i) > 0) {
                            lastNonZeroIndex = i;
                            break;
                        }
                    }
                    long infCount = bucketCounts.get(bucketCounts.size() - 1);
                    bucketCounts = bucketCounts.subList(0, lastNonZeroIndex + 1);
                    // infinite bucket count of 0 can be omitted
                    bucketCounts.add(infCount);
                }
            }

            // no-op histogram
            if (bucketCounts.isEmpty()) {
                bucketCounts.add(0L);
            }

            List<Double> bucketBoundaries = Arrays.stream(histogram)
                .map(countAtBucket -> timeDomain ? countAtBucket.bucket(getBaseTimeUnit()) : countAtBucket.bucket())
                .collect(toCollection(ArrayList::new));

            if (bucketBoundaries.size() == 1) {
                bucketBoundaries.remove(Double.POSITIVE_INFINITY);
            }

            // trim bucket boundaries to match bucket count trimming
            if (bucketBoundaries.size() != bucketCounts.size() - 1) {
                bucketBoundaries = bucketBoundaries.subList(0, bucketCounts.size() - 1);
            }

            // Stackdriver requires at least one explicit bucket bound
            if (bucketBoundaries.isEmpty()) {
                bucketBoundaries.add(0.0);
            }

            return Distribution.newBuilder()
                // is the mean optional? better to not send as it is for a different time
                // window than the histogram
                .setMean(timeDomain ? snapshot.mean(getBaseTimeUnit()) : snapshot.mean())
                .setCount(cumulativeCount)
                .setBucketOptions(Distribution.BucketOptions.newBuilder()
                    .setExplicitBuckets(
                            Distribution.BucketOptions.Explicit.newBuilder().addAllBounds(bucketBoundaries).build())
                    .build())
                .addAllBucketCounts(bucketCounts)
                .build();
        }

    }

}
