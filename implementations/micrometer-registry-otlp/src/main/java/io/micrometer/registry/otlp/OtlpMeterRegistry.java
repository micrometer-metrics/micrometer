/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import io.micrometer.core.instrument.step.StepCounter;
import io.micrometer.core.instrument.step.StepFunctionCounter;
import io.micrometer.core.instrument.step.StepFunctionTimer;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.registry.otlp.internal.CumulativeBase2ExponentialHistogram;
import io.micrometer.registry.otlp.internal.DeltaBase2ExponentialHistogram;
import io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE;
import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA;

/**
 * Publishes meters in OTLP (OpenTelemetry Protocol) format. HTTP with Protobuf encoding
 * is the only option currently supported.
 *
 * @author Tommy Ludwig
 * @author Lenin Jaganathan
 * @author Jonatan Ivanov
 * @since 1.9.0
 */
public class OtlpMeterRegistry extends PushMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("otlp-metrics-publisher");

    private static final ExponentialHistogramDataPoint.Buckets EMPTY_EXPONETIAL_HISTOGRAM_BUCKETS = ExponentialHistogramDataPoint.Buckets
        .newBuilder()
        .addAllBucketCounts(Collections.emptyList())
        .build();

    private static final double[] EMPTY_SLO_WITH_POSITIVE_INF = new double[] { Double.POSITIVE_INFINITY };

    private final InternalLogger logger = InternalLoggerFactory.getInstance(OtlpMeterRegistry.class);

    private final OtlpConfig config;

    private final HttpSender httpSender;

    private final Resource resource;

    private final io.opentelemetry.proto.metrics.v1.AggregationTemporality otlpAggregationTemporality;

    private final TimeUnit baseTimeUnit;

    private long deltaAggregationTimeUnixNano = 0L;

    @Nullable
    private ScheduledExecutorService meterPollingService;

    public OtlpMeterRegistry() {
        this(OtlpConfig.DEFAULT, Clock.SYSTEM);
    }

    public OtlpMeterRegistry(OtlpConfig config, Clock clock) {
        this(config, clock, new HttpUrlConnectionSender());
    }

    // not public until we decide what we want to expose in public API
    // HttpSender may not be a good idea if we will support a non-HTTP transport
    private OtlpMeterRegistry(OtlpConfig config, Clock clock, HttpSender httpSender) {
        super(config, clock);
        this.config = config;
        this.baseTimeUnit = config.baseTimeUnit();
        this.httpSender = httpSender;
        this.resource = Resource.newBuilder().addAllAttributes(getResourceAttributes()).build();
        this.otlpAggregationTemporality = AggregationTemporality
            .toOtlpAggregationTemporality(config.aggregationTemporality());
        setDeltaAggregationTimeUnixNano();
        config().namingConvention(NamingConvention.dot);
        start(DEFAULT_THREAD_FACTORY);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        super.start(threadFactory);

        if (config.enabled() && isDelta()) {
            this.meterPollingService = Executors.newSingleThreadScheduledExecutor(threadFactory);
            this.meterPollingService.scheduleAtFixedRate(this::pollMetersToRollover, getInitialDelay(),
                    config.step().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (this.meterPollingService != null) {
            this.meterPollingService.shutdown();
        }
    }

    @Override
    protected void publish() {
        if (isDelta()) {
            setDeltaAggregationTimeUnixNano();
        }
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            List<Metric> metrics = batch.stream()
                .map(meter -> meter.match(this::writeGauge, this::writeCounter, this::writeHistogramSupport,
                        this::writeHistogramSupport, this::writeHistogramSupport, this::writeGauge,
                        this::writeFunctionCounter, this::writeFunctionTimer, this::writeMeter))
                .collect(Collectors.toList());

            try {
                ExportMetricsServiceRequest request = ExportMetricsServiceRequest.newBuilder()
                    .addResourceMetrics(ResourceMetrics.newBuilder()
                        .setResource(this.resource)
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                            // we don't have instrumentation library/version
                            // attached to meters; leave unknown for now
                            // .setScope(InstrumentationScope.newBuilder().setName("").setVersion("").build())
                            .addAllMetrics(metrics)
                            .build())
                        .build())
                    .build();
                HttpSender.Request.Builder httpRequest = this.httpSender.post(this.config.url())
                    .withContent("application/x-protobuf", request.toByteArray());
                this.config.headers().forEach(httpRequest::withHeader);
                httpRequest.send();
            }
            catch (Throwable e) {
                logger.warn("Failed to publish metrics to OTLP receiver", e);
            }
        }
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return isCumulative() ? new OtlpCumulativeCounter(id, this.clock)
                : new StepCounter(id, this.clock, config.step().toMillis());
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        return isCumulative()
                ? new OtlpCumulativeTimer(id, this.clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                        config)
                : new OtlpStepTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(), config);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return isCumulative()
                ? new OtlpCumulativeDistributionSummary(id, this.clock, distributionStatisticConfig, scale, config)
                : new OtlpStepDistributionSummary(id, clock, distributionStatisticConfig, scale, config);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return isCumulative()
                ? new OtlpCumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit,
                        getBaseTimeUnit(), this.clock)
                : new StepFunctionTimer<>(id, clock, config.step().toMillis(), obj, countFunction, totalTimeFunction,
                        totalTimeFunctionUnit, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return isCumulative() ? new OtlpCumulativeFunctionCounter<>(id, obj, countFunction, this.clock)
                : new StepFunctionCounter<>(id, clock, config.step().toMillis(), obj, countFunction);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return isCumulative()
                ? new OtlpCumulativeLongTaskTimer(id, this.clock, getBaseTimeUnit(), distributionStatisticConfig)
                : new DefaultLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig, false);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return baseTimeUnit;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
            .expiry(this.config.step())
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);
    }

    @Override
    public void close() {
        stop();
        if (!isPublishing() && isDelta()) {
            if (!isDataPublishedForCurrentStep()) {
                // Data was not published for the current step. So, we should flush that
                // first.
                try {
                    this.publish();
                }
                catch (Throwable e) {
                    logger.warn(
                            "Unexpected exception thrown while publishing metrics for " + getClass().getSimpleName(),
                            e);
                }
            }
            getMeters().forEach(this::closingRollover);
        }
        super.close();
    }

    private boolean isDataPublishedForCurrentStep() {
        long currentTimeInMillis = clock.wallTime();
        return (getLastScheduledPublishStartTime() / config.step().toMillis()) >= (currentTimeInMillis
                / config.step().toMillis());
    }

    // Either we do this or make StepMeter public
    // and still call OtlpStepTimer and OtlpStepDistributionSummary separately.
    private void closingRollover(Meter meter) {
        if (meter instanceof StepCounter) {
            ((StepCounter) meter)._closingRollover();
        }
        else if (meter instanceof StepFunctionCounter) {
            ((StepFunctionCounter<?>) meter)._closingRollover();
        }
        else if (meter instanceof StepFunctionTimer) {
            ((StepFunctionTimer<?>) meter)._closingRollover();
        }
        else if (meter instanceof OtlpStepTimer) {
            ((OtlpStepTimer) meter)._closingRollover();
        }
        else if (meter instanceof OtlpStepDistributionSummary) {
            ((OtlpStepDistributionSummary) meter)._closingRollover();
        }
    }

    // VisibleForTesting
    Metric writeMeter(Meter meter) {
        // TODO support writing custom meters
        // one gauge per measurement
        return getMetricBuilder(meter.getId()).build();
    }

    // VisibleForTesting
    Metric writeGauge(Gauge gauge) {
        return getMetricBuilder(gauge.getId())
            .setGauge(io.opentelemetry.proto.metrics.v1.Gauge.newBuilder()
                .addDataPoints(NumberDataPoint.newBuilder()
                    .setTimeUnixNano(getTimeUnixNano())
                    .setAsDouble(gauge.value())
                    .addAllAttributes(getTagsForId(gauge.getId()))
                    .build()))
            .build();
    }

    // VisibleForTesting
    Metric writeCounter(Counter counter) {
        return writeSum(counter, counter::count);
    }

    // VisibleForTesting
    Metric writeFunctionCounter(FunctionCounter functionCounter) {
        return writeSum(functionCounter, functionCounter::count);
    }

    private Metric writeSum(Meter meter, DoubleSupplier count) {
        return getMetricBuilder(meter.getId())
            .setSum(Sum.newBuilder()
                .addDataPoints(NumberDataPoint.newBuilder()
                    .setStartTimeUnixNano(getStartTimeNanos(meter))
                    .setTimeUnixNano(getTimeUnixNano())
                    .setAsDouble(count.getAsDouble())
                    .addAllAttributes(getTagsForId(meter.getId()))
                    .build())
                .setIsMonotonic(true)
                .setAggregationTemporality(otlpAggregationTemporality)
                .build())
            .build();
    }

    /**
     * This will poll the values from meters, which will cause a roll over for Step-meters
     * if past the step boundary. This gives some control over when roll over happens
     * separate from when publishing happens. This method is almost the same as the one in
     * {@link StepMeterRegistry} it is subtly different from it in that this uses
     * {@code takeSnapshot()} to roll over the timers/summaries as OtlpDeltaTimer is using
     * a {@code StepValue} for maintaining distributions.
     */
    // VisibleForTesting
    void pollMetersToRollover() {
        this.getMeters()
            .forEach(m -> m.match(gauge -> null, Counter::count, Timer::takeSnapshot, DistributionSummary::takeSnapshot,
                    meter -> null, meter -> null, FunctionCounter::count, FunctionTimer::count, meter -> null));
    }

    private long getInitialDelay() {
        long stepMillis = config.step().toMillis();
        // schedule one millisecond into the next step
        return stepMillis - (clock.wallTime() % stepMillis) + 1;
    }

    // VisibleForTesting
    Metric writeHistogramSupport(HistogramSupport histogramSupport) {
        Metric.Builder metricBuilder = getMetricBuilder(histogramSupport.getId());
        boolean isTimeBased = histogramSupport instanceof Timer || histogramSupport instanceof LongTaskTimer;
        HistogramSnapshot histogramSnapshot = histogramSupport.takeSnapshot();

        Iterable<? extends KeyValue> tags = getTagsForId(histogramSupport.getId());
        long startTimeNanos = getStartTimeNanos(histogramSupport);
        double total = isTimeBased ? histogramSnapshot.total(getBaseTimeUnit()) : histogramSnapshot.total();
        long count = histogramSnapshot.count();

        // if percentiles configured, use summary
        if (histogramSnapshot.percentileValues().length != 0) {
            SummaryDataPoint.Builder summaryData = SummaryDataPoint.newBuilder()
                .addAllAttributes(tags)
                .setStartTimeUnixNano(startTimeNanos)
                .setTimeUnixNano(getTimeUnixNano())
                .setSum(total)
                .setCount(count);
            for (ValueAtPercentile percentile : histogramSnapshot.percentileValues()) {
                summaryData.addQuantileValues(SummaryDataPoint.ValueAtQuantile.newBuilder()
                    .setQuantile(percentile.percentile())
                    .setValue(TimeUtils.convert(percentile.value(), TimeUnit.NANOSECONDS, getBaseTimeUnit())));
            }
            metricBuilder.setSummary(Summary.newBuilder().addDataPoints(summaryData));
            return metricBuilder.build();
        }

        ExponentialHistogramSnapShot exponentialHistogramSnapShot = getExponentialHistogramSnapShot(histogramSupport);
        if (exponentialHistogramSnapShot != null) {
            ExponentialHistogramDataPoint.Builder exponentialDataPoint = ExponentialHistogramDataPoint.newBuilder()
                .addAllAttributes(tags)
                .setStartTimeUnixNano(startTimeNanos)
                .setTimeUnixNano(getTimeUnixNano())
                .setCount(count)
                .setSum(total)
                .setScale(exponentialHistogramSnapShot.scale())
                .setZeroCount(exponentialHistogramSnapShot.zeroCount())
                .setZeroThreshold(exponentialHistogramSnapShot.zeroThreshold())
                .setPositive(ExponentialHistogramDataPoint.Buckets.newBuilder()
                    .addAllBucketCounts(exponentialHistogramSnapShot.bucketsCount())
                    .setOffset(exponentialHistogramSnapShot.offset())
                    .build())
                // Micrometer doesn't support negative recordings.
                .setNegative(EMPTY_EXPONETIAL_HISTOGRAM_BUCKETS);

            if (isDelta()) {
                exponentialDataPoint
                    .setMax(isTimeBased ? histogramSnapshot.max(getBaseTimeUnit()) : histogramSnapshot.max());
            }

            return metricBuilder
                .setExponentialHistogram(ExponentialHistogram.newBuilder()
                    .setAggregationTemporality(otlpAggregationTemporality)
                    .addDataPoints(exponentialDataPoint)
                    .build())
                .build();
        }

        HistogramDataPoint.Builder histogramDataPoint = HistogramDataPoint.newBuilder()
            .addAllAttributes(tags)
            .setStartTimeUnixNano(startTimeNanos)
            .setTimeUnixNano(getTimeUnixNano())
            .setSum(total)
            .setCount(count);

        if (isDelta()) {
            histogramDataPoint.setMax(isTimeBased ? histogramSnapshot.max(getBaseTimeUnit()) : histogramSnapshot.max());
        }
        // if histogram enabled, add histogram buckets
        if (histogramSnapshot.histogramCounts().length != 0) {
            for (CountAtBucket countAtBucket : histogramSnapshot.histogramCounts()) {
                if (countAtBucket.bucket() != Double.POSITIVE_INFINITY) {
                    // OTLP expects explicit bounds to not contain POSITIVE_INFINITY but
                    // there should be a
                    // bucket count representing values between last bucket and
                    // POSITIVE_INFINITY.
                    histogramDataPoint.addExplicitBounds(
                            isTimeBased ? countAtBucket.bucket(getBaseTimeUnit()) : countAtBucket.bucket());
                }
                histogramDataPoint.addBucketCounts((long) countAtBucket.count());
            }
            metricBuilder.setHistogram(io.opentelemetry.proto.metrics.v1.Histogram.newBuilder()
                .setAggregationTemporality(otlpAggregationTemporality)
                .addDataPoints(histogramDataPoint));
            return metricBuilder.build();
        }

        return metricBuilder
            .setHistogram(io.opentelemetry.proto.metrics.v1.Histogram.newBuilder()
                .setAggregationTemporality(otlpAggregationTemporality)
                .addDataPoints(histogramDataPoint))
            .build();
    }

    @Nullable
    private static ExponentialHistogramSnapShot getExponentialHistogramSnapShot(
            final HistogramSupport histogramSupport) {
        if (histogramSupport instanceof OtlpHistogramSupport) {
            return ((OtlpHistogramSupport) histogramSupport).getExponentialHistogramSnapShot();
        }

        return null;
    }

    // VisibleForTesting
    Metric writeFunctionTimer(FunctionTimer functionTimer) {
        return getMetricBuilder(functionTimer.getId())
            .setHistogram(io.opentelemetry.proto.metrics.v1.Histogram.newBuilder()
                .addDataPoints(HistogramDataPoint.newBuilder()
                    .addAllAttributes(getTagsForId(functionTimer.getId()))
                    .setStartTimeUnixNano(getStartTimeNanos((functionTimer)))
                    .setTimeUnixNano(getTimeUnixNano())
                    .setSum(functionTimer.totalTime(getBaseTimeUnit()))
                    .setCount((long) functionTimer.count()))
                .setAggregationTemporality(otlpAggregationTemporality))
            .build();
    }

    private boolean isCumulative() {
        return this.otlpAggregationTemporality == AGGREGATION_TEMPORALITY_CUMULATIVE;
    }

    private boolean isDelta() {
        return this.otlpAggregationTemporality == AGGREGATION_TEMPORALITY_DELTA;
    }

    // VisibleForTesting
    void setDeltaAggregationTimeUnixNano() {
        this.deltaAggregationTimeUnixNano = (clock.wallTime() / config.step().toMillis()) * config.step().toNanos();
    }

    private long getTimeUnixNano() {
        return isCumulative() ? TimeUnit.MILLISECONDS.toNanos(this.clock.wallTime()) : deltaAggregationTimeUnixNano;
    }

    private long getStartTimeNanos(Meter meter) {
        return isCumulative() ? ((StartTimeAwareMeter) meter).getStartTimeNanos()
                : deltaAggregationTimeUnixNano - config.step().toNanos();
    }

    private Metric.Builder getMetricBuilder(Meter.Id id) {
        Metric.Builder builder = Metric.newBuilder().setName(getConventionName(id));
        if (id.getBaseUnit() != null) {
            builder.setUnit(id.getBaseUnit());
        }
        if (id.getDescription() != null) {
            builder.setDescription(id.getDescription());
        }
        return builder;
    }

    private Iterable<? extends KeyValue> getTagsForId(Meter.Id id) {
        return id.getTags()
            .stream()
            .map(tag -> createKeyValue(tag.getKey(), tag.getValue()))
            .collect(Collectors.toList());
    }

    // VisibleForTesting
    static KeyValue createKeyValue(String key, String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }

    // VisibleForTesting
    Iterable<KeyValue> getResourceAttributes() {
        boolean serviceNameProvided = false;
        List<KeyValue> attributes = new ArrayList<>();
        attributes.add(createKeyValue("telemetry.sdk.name", "io.micrometer"));
        attributes.add(createKeyValue("telemetry.sdk.language", "java"));
        String micrometerCoreVersion = MeterRegistry.class.getPackage().getImplementationVersion();
        if (micrometerCoreVersion != null) {
            attributes.add(createKeyValue("telemetry.sdk.version", micrometerCoreVersion));
        }
        for (Map.Entry<String, String> keyValue : this.config.resourceAttributes().entrySet()) {
            if ("service.name".equals(keyValue.getKey())) {
                serviceNameProvided = true;
            }
            attributes.add(createKeyValue(keyValue.getKey(), keyValue.getValue()));
        }
        if (!serviceNameProvided) {
            attributes.add(createKeyValue("service.name", "unknown_service"));
        }
        return attributes;
    }

    static Histogram getHistogram(Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            OtlpConfig otlpConfig) {
        return getHistogram(clock, distributionStatisticConfig, otlpConfig, null);
    }

    static Histogram getHistogram(final Clock clock, final DistributionStatisticConfig distributionStatisticConfig,
            final OtlpConfig otlpConfig, @Nullable final TimeUnit baseTimeUnit) {
        // While publishing to OTLP, we export either Histogram datapoint (Explicit Bucket
        // or Exponential) / Summary
        // datapoint. So, we will make the histogram either of them and not both.
        // Though AbstractTimer/Distribution Summary prefers publishing percentiles,
        // exporting of histograms over percentiles is preferred in OTLP.
        if (distributionStatisticConfig.isPublishingHistogram()) {
            if (histogramFlavour(otlpConfig.histogramFlavour(),
                    distributionStatisticConfig) == HistogramFlavour.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM) {
                Double minimumExpectedValue = distributionStatisticConfig.getMinimumExpectedValueAsDouble();
                if (minimumExpectedValue == null) {
                    minimumExpectedValue = 0.0;
                }

                return otlpConfig.aggregationTemporality() == AggregationTemporality.DELTA
                        ? new DeltaBase2ExponentialHistogram(otlpConfig.maxScale(), otlpConfig.maxBucketCount(),
                                minimumExpectedValue, baseTimeUnit, clock, otlpConfig.step().toMillis())
                        : new CumulativeBase2ExponentialHistogram(otlpConfig.maxScale(), otlpConfig.maxBucketCount(),
                                minimumExpectedValue, baseTimeUnit);
            }

            Histogram explicitBucketHistogram = getExplicitBucketHistogram(clock, distributionStatisticConfig,
                    otlpConfig.aggregationTemporality(), otlpConfig.step().toMillis());
            if (explicitBucketHistogram != null) {
                return explicitBucketHistogram;
            }
        }

        if (distributionStatisticConfig.isPublishingPercentiles()) {
            return new TimeWindowPercentileHistogram(clock, distributionStatisticConfig, false);
        }
        return NoopHistogram.INSTANCE;
    }

    static HistogramFlavour histogramFlavour(HistogramFlavour preferredHistogramFlavour,
            DistributionStatisticConfig distributionStatisticConfig) {

        final double[] serviceLevelObjectiveBoundaries = distributionStatisticConfig
            .getServiceLevelObjectiveBoundaries();
        if (distributionStatisticConfig.isPublishingHistogram()
                && preferredHistogramFlavour == HistogramFlavour.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM
                && (serviceLevelObjectiveBoundaries == null || serviceLevelObjectiveBoundaries.length == 0)) {
            return HistogramFlavour.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
        }
        return HistogramFlavour.EXPLICIT_BUCKET_HISTOGRAM;
    }

    @Nullable
    private static Histogram getExplicitBucketHistogram(final Clock clock,
            final DistributionStatisticConfig distributionStatisticConfig,
            final AggregationTemporality aggregationTemporality, final long stepMillis) {

        double[] sloWithPositiveInf = getSloWithPositiveInf(distributionStatisticConfig);
        if (AggregationTemporality.isCumulative(aggregationTemporality)) {
            return new TimeWindowFixedBoundaryHistogram(clock, DistributionStatisticConfig.builder()
                // effectively never roll over
                .expiry(Duration.ofDays(1825))
                .serviceLevelObjectives(sloWithPositiveInf)
                .percentiles()
                .bufferLength(1)
                .build()
                .merge(distributionStatisticConfig), true, false);
        }
        if (AggregationTemporality.isDelta(aggregationTemporality) && stepMillis > 0) {
            return new OtlpStepBucketHistogram(clock, stepMillis,
                    DistributionStatisticConfig.builder()
                        .serviceLevelObjectives(sloWithPositiveInf)
                        .build()
                        .merge(distributionStatisticConfig),
                    true, false);
        }

        return null;
    }

    // VisibleForTesting
    static double[] getSloWithPositiveInf(DistributionStatisticConfig distributionStatisticConfig) {
        double[] sloBoundaries = distributionStatisticConfig.getServiceLevelObjectiveBoundaries();
        if (sloBoundaries == null || sloBoundaries.length == 0) {
            // When there are no SLO's associated with DistributionStatisticConfig we will
            // add one with Positive
            // Infinity. This will make sure we always have POSITIVE_INFINITY, and the
            // NavigableSet will make sure
            // duplicates if any will be ignored.
            return EMPTY_SLO_WITH_POSITIVE_INF;
        }

        boolean containsPositiveInf = Arrays.stream(sloBoundaries).anyMatch(value -> value == Double.POSITIVE_INFINITY);
        if (containsPositiveInf)
            return sloBoundaries;

        double[] sloWithPositiveInf = Arrays.copyOf(sloBoundaries, sloBoundaries.length + 1);
        sloWithPositiveInf[sloWithPositiveInf.length - 1] = Double.POSITIVE_INFINITY;
        return sloWithPositiveInf;
    }

}
