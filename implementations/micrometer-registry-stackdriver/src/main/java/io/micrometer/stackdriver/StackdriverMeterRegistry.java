package io.micrometer.stackdriver;

import com.google.api.Distribution;
import com.google.api.Metric;
import com.google.api.MetricDescriptor;
import com.google.api.MonitoredResource;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.monitoring.v3.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepDistributionSummary;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepTimer;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.Meter.Type.match;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

public class StackdriverMeterRegistry extends StepMeterRegistry {
    private final Logger logger = LoggerFactory.getLogger(StackdriverMeterRegistry.class);
    private final StackdriverConfig config;

    /**
     * The "metric" type is meant as a catch-all when no other resource type is suitable, which
     * includes everything that Micrometer ships.
     * https://cloud.google.com/monitoring/custom-metrics/creating-metrics#which-resource
     */
    private static final String RESOURCE_TYPE = "metric";

    /**
     * Metric names for which we have posted a custom metric
     */
    private final Set<String> verifiedDescriptors = ConcurrentHashMap.newKeySet();

    @Nullable
    private final MetricServiceClient client;

    private volatile boolean warnedAboutMetricDescriptorPermissions = false;

    public StackdriverMeterRegistry(StackdriverConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory(), () -> MetricServiceSettings.newBuilder().build());
    }

    private StackdriverMeterRegistry(StackdriverConfig config, Clock clock, ThreadFactory threadFactory,
                                     Callable<MetricServiceSettings> metricServiceSettings) {
        super(config, clock);

        this.config = config;

        config().namingConvention(new StackdriverNamingConvention());

        MetricServiceClient clientOrNull = null;
        try {
            clientOrNull = MetricServiceClient.create(metricServiceSettings.call());
        } catch (Exception e) {
            logger.error("unable to configure Stackdriver monitoring", e);
        }

        this.client = clientOrNull;

        if (config.enabled())
            start(threadFactory);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (client != null) {
            super.start(threadFactory);
        }
    }

    @Override
    protected void publish() {
        if (client == null) {
            return;
        }

        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            BatchSession session = new BatchSession(Arrays.stream(config.resourceTags()).collect(Collectors.toSet()));

            Iterable<TimeSeries> series = batch.stream()
                    .flatMap(meter -> match(meter,
                            m -> createGauge(session, m),
                            m -> createCounter(session, m),
                            m -> createTimer(session, m),
                            m -> createSummary(session, m),
                            m -> createLongTaskTimer(session, m),
                            m -> createTimeGauge(session, m),
                            m -> createFunctionCounter(session, m),
                            m -> createFunctionTimer(session, m),
                            m -> createMeter(session, m)))
                    .collect(toList());

            try {
                client.createTimeSeries(CreateTimeSeriesRequest.newBuilder()
                        .setName(config.projectId())
                        .addAllTimeSeries(series)
                        .build());
            } catch (ApiException e) {
                logger.warn("failed to send metrics to stackdriver", e);
            }
        }
    }

    private Stream<TimeSeries> createMeter(BatchSession session, Meter m) {
        return stream(m.measure().spliterator(), false)
                .map(ms -> session.createTimeSeries(m, ms.getValue(), ms.getStatistic().getTagValueRepresentation()));
    }

    private Stream<TimeSeries> createFunctionTimer(BatchSession session, FunctionTimer timer) {
        Distribution.Builder distribution = Distribution.newBuilder()
                .setMean(timer.mean(getBaseTimeUnit()))
                .setCount((long) timer.count());

        return Stream.of(
                session.createTimeSeries(timer, distribution.build()),
                session.createTimeSeries(timer, timer.totalTime(getBaseTimeUnit()), "sum")
        );
    }

    private Stream<TimeSeries> createFunctionCounter(BatchSession session, FunctionCounter functionCounter) {
        return Stream.of(session.createTimeSeries(functionCounter, functionCounter.count(), null));
    }

    private Stream<TimeSeries> createTimeGauge(BatchSession session, TimeGauge timeGauge) {
        return Stream.of(session.createTimeSeries(timeGauge, timeGauge.value(getBaseTimeUnit()), null));
    }

    private Stream<TimeSeries> createSummary(BatchSession session, DistributionSummary summary) {
        HistogramSnapshot snapshot = summary.takeSnapshot();

        CountAtBucket[] histogram = snapshot.histogramCounts();
        Distribution.Builder distribution = Distribution.newBuilder()
                .setMean(snapshot.mean())
                .setCount(snapshot.count());

        if (histogram.length > 0) {
            Distribution.BucketOptions bucketOptions = Distribution.BucketOptions.newBuilder()
                    .setExplicitBuckets(Distribution.BucketOptions.Explicit.newBuilder()
                            .addAllBounds(Arrays.stream(histogram)
                                    .map(countAtBucket -> (double) countAtBucket.bucket())
                                    .collect(toList()))
                            .build())
                    .build();

            distribution
                    .setBucketOptions(bucketOptions)
                    .addAllBucketCounts(Arrays.stream(histogram)
                            .map(countAtBucket -> (long) countAtBucket.count())
                            .collect(Collectors.toList()));
        }

        return Stream.concat(
                Stream.of(
                        session.createTimeSeries(summary, distribution.build()),
                        session.createTimeSeries(summary, snapshot.total(), "sum"),
                        session.createTimeSeries(summary, snapshot.max(), "max")
                ),
                Arrays.stream(snapshot.percentileValues())
                        .map(valueAtPercentile -> session.createTimeSeries(summary, valueAtPercentile.value(),
                                "p" + DoubleFormat.decimalOrWhole(valueAtPercentile.percentile() * 100))));
    }

    private Stream<TimeSeries> createTimer(BatchSession session, Timer timer) {
        HistogramSnapshot snapshot = timer.takeSnapshot();

        CountAtBucket[] histogram = snapshot.histogramCounts();
        Distribution.Builder distribution = Distribution.newBuilder()
                .setMean(snapshot.mean(getBaseTimeUnit()))
                .setCount(snapshot.count());

        if (histogram.length > 0) {
            Distribution.BucketOptions bucketOptions = Distribution.BucketOptions.newBuilder()
                    .setExplicitBuckets(Distribution.BucketOptions.Explicit.newBuilder()
                            .addAllBounds(Arrays.stream(histogram)
                                    .map(countAtBucket -> countAtBucket.bucket(getBaseTimeUnit()))
                                    .collect(toList()))
                            .build())
                    .build();

            distribution
                    .setBucketOptions(bucketOptions)
                    .addAllBucketCounts(Arrays.stream(histogram)
                            .map(countAtBucket -> (long) countAtBucket.count())
                            .collect(Collectors.toList()));
        }

        return Stream.concat(
                Stream.of(
                        session.createTimeSeries(timer, distribution.build()),
                        session.createTimeSeries(timer, snapshot.total(getBaseTimeUnit()), "sum"),
                        session.createTimeSeries(timer, snapshot.max(getBaseTimeUnit()), "max")
                ),
                Arrays.stream(snapshot.percentileValues())
                        .map(valueAtPercentile -> session.createTimeSeries(timer, valueAtPercentile.value(getBaseTimeUnit()),
                                "p" + DoubleFormat.decimalOrWhole(valueAtPercentile.percentile() * 100))));
    }

    private Stream<TimeSeries> createGauge(BatchSession session, Gauge gauge) {
        return Stream.of(session.createTimeSeries(gauge, gauge.value(), null));
    }

    private Stream<TimeSeries> createCounter(BatchSession session, Counter counter) {
        return Stream.of(session.createTimeSeries(counter, counter.count(), null));
    }

    private Stream<TimeSeries> createLongTaskTimer(BatchSession session, LongTaskTimer longTaskTimer) {
        return Stream.of(
                session.createTimeSeries(longTaskTimer, longTaskTimer.activeTasks(), "activeTasks"),
                session.createTimeSeries(longTaskTimer, longTaskTimer.duration(getBaseTimeUnit()), "duration")
        );
    }

    private class BatchSession {
        private final Set<String> resourceTags;

        private BatchSession(Set<String> resourceTags) {
            this.resourceTags = resourceTags;
        }

        TimeSeries createTimeSeries(Meter meter, double value, @Nullable String statistic) {
            return createTimeSeries(meter.getId(), TypedValue.newBuilder().setDoubleValue(value).build(),
                    MetricDescriptor.ValueType.DOUBLE, statistic);
        }

        TimeSeries createTimeSeries(Meter meter, Distribution distribution) {
            return createTimeSeries(meter.getId(), TypedValue.newBuilder().setDistributionValue(distribution).build(),
                    MetricDescriptor.ValueType.DISTRIBUTION, null);
        }

        private TimeSeries createTimeSeries(Meter.Id id, TypedValue typedValue, MetricDescriptor.ValueType valueType,
                                            @Nullable String statistic) {
            if (client != null)
                createMetricDescriptorIfNecessary(client, id, statistic);

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
                            .putLabels("name", metricType)
                            .build())
                    .setMetricKind(MetricDescriptor.MetricKind.GAUGE) // https://cloud.google.com/monitoring/api/v3/metrics-details#metric-kinds
                    .setValueType(valueType)
                    .addPoints(Point.newBuilder().setValue(typedValue).build())
                    .build();
        }

        private String metricType(Meter.Id id, @Nullable String statistic) {
            StringBuilder metricType = new StringBuilder(config.pathPrefix()).append("/").append(getConventionName(id));
            if (statistic != null) {
                metricType.append("/").append(statistic);
            }
            return metricType.toString();
        }

        private void createMetricDescriptorIfNecessary(MetricServiceClient client, Meter.Id id, @Nullable String statistic) {
            if (!verifiedDescriptors.contains(id.getName())) {
                MetricDescriptor descriptor = MetricDescriptor.newBuilder()
                        .setType(metricType(id, statistic))
                        .setDescription(id.getDescription() == null ? "" : id.getDescription())
                        .setMetricKind(MetricDescriptor.MetricKind.GAUGE)
                        .setValueType(MetricDescriptor.ValueType.DOUBLE)
                        .build();

                ProjectName name = ProjectName.of(config.projectId());

                CreateMetricDescriptorRequest request = CreateMetricDescriptorRequest.newBuilder()
                        .setName(name.toString())
                        .setMetricDescriptor(descriptor)
                        .build();

                try {
                    client.createMetricDescriptor(request);
                    verifiedDescriptors.add(id.getName());
                } catch (ApiException e) {
                    if(StatusCode.Code.PERMISSION_DENIED.equals(e.getStatusCode().getCode())) {
                        if(!warnedAboutMetricDescriptorPermissions) {
                            // we'll assume from this point that all metric descriptors have already been created
                            logger.debug("credentials provided to stackdriver don't have permission to create metric descriptors");
                            warnedAboutMetricDescriptorPermissions = true;
                        }
                    } else {
                        logger.warn("failed to create metric descriptor in stackdriver for meter " + id, e);
                    }
                }
            }
        }
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new StepDistributionSummary(id, clock, distributionStatisticConfig, scale,
                config.step().toMillis(), false);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        return new StepTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                this.config.step().toMillis(), false);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static Builder builder(StackdriverConfig config) {
        return new Builder(config);
    }

    public static class Builder {
        private final StackdriverConfig config;
        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = Executors.defaultThreadFactory();
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
}
