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

import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import io.micrometer.core.instrument.step.StepCounter;
import io.micrometer.core.instrument.step.StepFunctionCounter;
import io.micrometer.core.instrument.step.StepFunctionTimer;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.exporter.internal.otlp.metrics.MetricsRequestMarshaler;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Publishes meters in OTLP (OpenTelemetry Protocol) format.
 *
 * @author Tommy Ludwig
 * @author Lenin Jaganathan
 * @author Jonatan Ivanov
 * @since 1.9.0
 */
public class OtlpMeterRegistry extends PushMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("otlp-metrics-publisher");

    private static final double[] EMPTY_SLO_WITH_POSITIVE_INF = new double[] { Double.POSITIVE_INFINITY };

    private static final String TELEMETRY_SDK_NAME = "telemetry.sdk.name";

    private static final String TELEMETRY_SDK_LANGUAGE = "telemetry.sdk.language";

    private static final String TELEMETRY_SDK_VERSION = "telemetry.sdk.version";

    private static final Set<String> RESERVED_RESOURCE_ATTRIBUTES = new HashSet<>(
            Arrays.asList(TELEMETRY_SDK_NAME, TELEMETRY_SDK_LANGUAGE, TELEMETRY_SDK_VERSION));

    private final InternalLogger logger = InternalLoggerFactory.getInstance(OtlpMeterRegistry.class);

    private final OtlpConfig config;

    private final OtlpMetricsSender metricsSender;

    private final HistogramFlavorPerMeterLookup histogramFlavorPerMeterLookup;

    private final MaxBucketsPerMeterLookup maxBucketsPerMeterLookup;

    private final Resource resource;

    private final AggregationTemporality aggregationTemporality;

    private final TimeUnit baseTimeUnit;

    private final @Nullable OtlpExemplarSamplerFactory exemplarSamplerFactory;

    // Time when the last scheduled rollOver has started. Applicable only for delta
    // flavour.
    private volatile long lastMeterRolloverStartTime = -1;

    private @Nullable ScheduledExecutorService meterPollingService;

    public OtlpMeterRegistry() {
        this(OtlpConfig.DEFAULT, Clock.SYSTEM);
    }

    public OtlpMeterRegistry(OtlpConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY);
    }

    /**
     * Create an {@code OtlpMeterRegistry} instance with an HTTP metrics exporter.
     * @param config config
     * @param clock clock
     * @param threadFactory thread factory
     * @since 1.14.0
     */
    public OtlpMeterRegistry(OtlpConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, createDefaultSender(config), null);
    }

    public OtlpMeterRegistry(OtlpConfig config, Clock clock, ThreadFactory threadFactory,
            OtlpMetricsSender metricsSender, @Nullable ExemplarContextProvider exemplarContextProvider) {
        super(config, clock);
        this.config = config;
        this.baseTimeUnit = config.baseTimeUnit();
        this.metricsSender = metricsSender;
        this.histogramFlavorPerMeterLookup = HistogramFlavorPerMeterLookup.DEFAULT;
        this.maxBucketsPerMeterLookup = MaxBucketsPerMeterLookup.DEFAULT;
        this.resource = createResource();
        this.aggregationTemporality = config.aggregationTemporality();
        this.exemplarSamplerFactory = exemplarContextProvider != null
                ? new OtlpExemplarSamplerFactory(exemplarContextProvider, clock, config) : null;
        config().namingConvention(NamingConvention.dot);
        start(threadFactory);
    }

    private static OtlpMetricsSender createDefaultSender(OtlpConfig config) {
        return new OtlpHttpMetricsSender(new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    /**
     * Construct an {@link OtlpMeterRegistry} using the Builder pattern.
     * @param config config for the registry; see {@link OtlpConfig#DEFAULT}
     * @return builder
     * @since 1.15.0
     */
    public static Builder builder(OtlpConfig config) {
        return new Builder(config);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void start(ThreadFactory threadFactory) {
        super.start(threadFactory);

        if (config.enabled() && isDelta()) {
            this.meterPollingService = Executors.newSingleThreadScheduledExecutor(threadFactory);
            this.meterPollingService.scheduleAtFixedRate(this::pollMetersToRollover, getInitialDelay(),
                    config.step().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected String startMessage() {
        return String.format("Publishing metrics for %s every %s to %s with resource attributes %s",
                getClass().getSimpleName(), TimeUtils.format(config.step()), config.url(), config.resourceAttributes());
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
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            OtlpMetricConverter otlpMetricConverter = new OtlpMetricConverter(clock, config.step(), getBaseTimeUnit(),
                    config.aggregationTemporality(), config().namingConvention(), config.publishMaxGaugeForHistograms(),
                    this.resource);
            otlpMetricConverter.addMeters(batch);

            Collection<MetricData> metrics = otlpMetricConverter.getAllMetrics();
            if (!metrics.isEmpty()) {
                try {
                    MetricsRequestMarshaler marshaler = MetricsRequestMarshaler.create(metrics);
                    ByteArrayOutputStream os = new ByteArrayOutputStream(marshaler.getBinarySerializedSize());
                    marshaler.writeBinaryTo(os);

                    String readableData;
                    try {
                        ByteArrayOutputStream jsonOs = new ByteArrayOutputStream();
                        marshaler.writeJsonTo(jsonOs);
                        readableData = new String(jsonOs.toByteArray(), StandardCharsets.UTF_8);
                    }
                    catch (Throwable t) {
                        readableData = metrics.toString();
                    }

                    OtlpMetricsSender.Request request = OtlpMetricsSender.Request.builder(os.toByteArray())
                        .address(config.url())
                        .headers(config.headers())
                        .compressionMode(config.compressionMode())
                        .readableMetricsData(readableData)
                        .build();

                    this.metricsSender.send(request);
                }
                catch (Exception e) {
                    logger.warn(String.format("Failed to publish metrics to OTLP receiver (context: %s)",
                            getConfigurationContext()), e);
                }
            }
        }
    }

    /**
     * Get the configuration context.
     * @return A message containing enough information for the log reader to figure out
     * what configuration details may have contributed to the failure.
     */
    private String getConfigurationContext() {
        // While other values may contribute to failures, these two are most common
        return "url=" + config.url() + ", resource-attributes=" + config.resourceAttributes();
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return isCumulative() ? new OtlpCumulativeCounter(id, this.clock, exemplarSamplerFactory)
                : new OtlpStepCounter(id, this.clock, config.step().toMillis(), exemplarSamplerFactory);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        return isCumulative()
                ? new OtlpCumulativeTimer(id, this.clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                        getHistogram(id, distributionStatisticConfig, getBaseTimeUnit()), exemplarSamplerFactory)
                : new OtlpStepTimer(id, clock, pauseDetector,
                        getHistogram(id, distributionStatisticConfig, getBaseTimeUnit()), config,
                        exemplarSamplerFactory);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return isCumulative()
                ? new OtlpCumulativeDistributionSummary(id, clock, distributionStatisticConfig, scale,
                        getHistogram(id, distributionStatisticConfig), exemplarSamplerFactory)
                : new OtlpStepDistributionSummary(id, clock, scale, getHistogram(id, distributionStatisticConfig),
                        config, exemplarSamplerFactory);
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
        if (config.enabled() && isDelta() && !isClosed()) {
            if (shouldPublishDataForLastStep() && !isPublishing()) {
                // Data was not published for the last step. So, we should flush that
                // first.
                try {
                    publish();
                }
                catch (Throwable e) {
                    logger.warn(
                            "Unexpected exception thrown while publishing metrics for " + getClass().getSimpleName(),
                            e);
                }
            }
            else if (isPublishing()) {
                waitForInProgressScheduledPublish();
            }
            getMeters().forEach(this::closingRollover);
        }
        else if (config.enabled() && isCumulative() && !isClosed()) {
            if (isPublishing()) {
                waitForInProgressScheduledPublish();
            }
            getMeters().stream()
                .filter(meter -> meter instanceof OtlpExemplarsSupport)
                .map(meter -> (OtlpExemplarsSupport) meter)
                .forEach(OtlpExemplarsSupport::closingExemplarsRollover);
        }

        super.close();
    }

    private void closingRollover(Meter meter) {
        if (isCumulative() && meter instanceof OtlpExemplarsSupport) {
            ((OtlpExemplarsSupport) meter).closingExemplarsRollover();
        }
        meter.match(gauge -> null, this::rollover, this::rollover, this::rollover, meter1 -> null, meter1 -> null,
                functionCounter -> null, functionTimer -> null, meter1 -> null);
    }

    private double rollover(Counter counter) {
        if (counter instanceof StepCounter) {
            ((StepCounter) counter)._closingRollover();
        }

        return counter.count();
    }

    private HistogramSnapshot rollover(HistogramSupport histogramSupport) {
        if (histogramSupport instanceof OtlpStepTimer) {
            ((OtlpStepTimer) histogramSupport)._closingRollover();
        }
        else if (histogramSupport instanceof OtlpStepDistributionSummary) {
            ((OtlpStepDistributionSummary) histogramSupport)._closingRollover();
        }

        return histogramSupport.takeSnapshot();
    }

    /**
     * Determine if data for the last step should be published during close. The decision
     * is made based on when the last scheduled rollover started.
     */
    // VisibleForTesting
    boolean shouldPublishDataForLastStep() {
        if (this.lastMeterRolloverStartTime == -1) {
            return true;
        }

        long lastMeterRolloverDuration = clock.wallTime() - this.lastMeterRolloverStartTime;
        long allowableDelayDuration = (config.step().toMillis() / 2);
        return lastMeterRolloverDuration > allowableDelayDuration;
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
        this.lastMeterRolloverStartTime = clock.wallTime();
        this.getMeters()
            .forEach(m -> m.match(gauge -> null, this::poll, this::poll, this::poll, meter -> null, meter -> null,
                    FunctionCounter::count, FunctionTimer::count, meter -> null));
    }

    private double poll(Counter counter) {
        if (counter instanceof OtlpExemplarsSupport) {
            ((OtlpExemplarsSupport) counter).exemplars();
        }
        return counter.count();
    }

    private HistogramSnapshot poll(HistogramSupport histogramSupport) {
        if (histogramSupport instanceof OtlpExemplarsSupport) {
            ((OtlpExemplarsSupport) histogramSupport).exemplars();
        }
        return histogramSupport.takeSnapshot();
    }

    private long getInitialDelay() {
        long stepMillis = config.step().toMillis();
        // schedule one millisecond into the next step
        return stepMillis - (clock.wallTime() % stepMillis) + 1;
    }

    private boolean isCumulative() {
        return this.aggregationTemporality == AggregationTemporality.CUMULATIVE;
    }

    private boolean isDelta() {
        return this.aggregationTemporality == AggregationTemporality.DELTA;
    }

    // VisibleForTesting
    Resource getResource() {
        return this.resource;
    }

    private Resource createResource() {
        AttributesBuilder builder = Attributes.builder();
        builder.put(TELEMETRY_SDK_NAME, "io.micrometer");
        builder.put(TELEMETRY_SDK_LANGUAGE, "java");
        String micrometerCoreVersion = MeterRegistry.class.getPackage().getImplementationVersion();
        if (micrometerCoreVersion != null) {
            builder.put(TELEMETRY_SDK_VERSION, micrometerCoreVersion);
        }
        boolean serviceNameProvided = false;
        for (Map.Entry<String, String> keyValue : this.config.resourceAttributes().entrySet()) {
            if ("service.name".equals(keyValue.getKey())) {
                serviceNameProvided = true;
            }
            if (RESERVED_RESOURCE_ATTRIBUTES.contains(keyValue.getKey())) {
                logger.warn("Resource attribute {} is reserved and will be ignored", keyValue.getKey());
                continue;
            }
            builder.put(keyValue.getKey(), keyValue.getValue());
        }
        if (!serviceNameProvided) {
            builder.put("service.name", "unknown_service");
        }
        return Resource.create(builder.build());
    }

    private @Nullable Histogram getHistogram(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return getHistogram(id, distributionStatisticConfig, null);
    }

    private @Nullable Histogram getHistogram(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            @Nullable TimeUnit baseTimeUnit) {
        if (distributionStatisticConfig.isPublishingHistogram()) {
            HistogramFlavor flavor = this.histogramFlavorPerMeterLookup
                .getHistogramFlavor(config.histogramFlavorPerMeter(), id);
            if (flavor == null) {
                flavor = config.histogramFlavor();
            }

            if (flavor == HistogramFlavor.EXPLICIT_BUCKET_HISTOGRAM) {
                return getStepBucketHistogram(distributionStatisticConfig, baseTimeUnit);
            }

            Integer maxBuckets = this.maxBucketsPerMeterLookup.getMaxBuckets(config.maxBucketsPerMeter(), id);
            if (maxBuckets == null) {
                maxBuckets = config.maxBucketCount();
            }

            double maxScale = Math.min(config.maxScale(), 20);

            if (AggregationTemporality.isCumulative(aggregationTemporality)) {
                return new CumulativeBase2ExponentialHistogram((int) maxScale, maxBuckets, 0.0, baseTimeUnit,
                        exemplarSamplerFactory);
            }

            long stepMillis = config.step().toMillis();
            if (stepMillis > 0) {
                return new DeltaBase2ExponentialHistogram((int) maxScale, maxBuckets, 0.0, baseTimeUnit, clock,
                        stepMillis, exemplarSamplerFactory);
            }
        }

        if (distributionStatisticConfig.isPublishingPercentiles()) {
            return new TimeWindowPercentileHistogram(clock, distributionStatisticConfig, false);
        }

        return NoopHistogram.INSTANCE;
    }

    private @Nullable Histogram getStepBucketHistogram(DistributionStatisticConfig distributionStatisticConfig,
            @Nullable TimeUnit baseTimeUnit) {
        double[] sloWithPositiveInf = getSloWithPositiveInf(distributionStatisticConfig);
        long stepMillis = config.step().toMillis();
        if (AggregationTemporality.isCumulative(aggregationTemporality)) {
            DistributionStatisticConfig merged = DistributionStatisticConfig.builder()
                .serviceLevelObjectives(sloWithPositiveInf)
                .bufferLength(1)
                .build()
                .merge(distributionStatisticConfig);

            DistributionStatisticConfig cumulativeConfig = DistributionStatisticConfig.builder()
                .percentiles(merged.getPercentiles())
                .percentilePrecision(merged.getPercentilePrecision())
                .minimumExpectedValue(merged.getMinimumExpectedValueAsDouble())
                .maximumExpectedValue(merged.getMaximumExpectedValueAsDouble())
                .expiry(java.time.Duration.ofDays(1825))
                .bufferLength(1)
                .serviceLevelObjectives(sloWithPositiveInf)
                .build();

            return new OtlpCumulativeBucketHistogram(clock, cumulativeConfig, exemplarSamplerFactory,
                    baseTimeUnit != null);
        }
        if (AggregationTemporality.isDelta(aggregationTemporality) && stepMillis > 0) {
            return new OtlpStepBucketHistogram(clock, stepMillis,
                    DistributionStatisticConfig.builder()
                        .serviceLevelObjectives(sloWithPositiveInf)
                        .build()
                        .merge(distributionStatisticConfig),
                    exemplarSamplerFactory, baseTimeUnit != null);
        }

        return null;
    }

    // VisibleForTesting
    static double[] getSloWithPositiveInf(DistributionStatisticConfig distributionStatisticConfig) {
        double[] sloBoundaries = distributionStatisticConfig.getServiceLevelObjectiveBoundaries();
        if (sloBoundaries == null || sloBoundaries.length == 0) {
            NavigableSet<Double> histogramBuckets = distributionStatisticConfig.getHistogramBuckets(false);
            if (histogramBuckets != null && !histogramBuckets.isEmpty()) {
                sloBoundaries = histogramBuckets.stream().mapToDouble(Double::doubleValue).toArray();
            }
        }
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

    /**
     * Overridable lookup mechanism for {@link HistogramFlavor}.
     */
    // VisibleForTesting
    @FunctionalInterface
    interface HistogramFlavorPerMeterLookup {

        /**
         * Default implementation.
         */
        HistogramFlavorPerMeterLookup DEFAULT = OtlpMeterRegistry::lookup;

        /**
         * Looks up the histogram flavor to use on a per-meter level. This will override
         * the default {@link OtlpConfig#histogramFlavor()} for matching Meters.
         * {@link OtlpConfig#histogramFlavorPerMeter()} provides the data while this
         * method provides the logic for the lookup, and you can override them
         * independently.
         * @param perMeterMapping configured mapping data
         * @param id the {@link Meter.Id} the {@link HistogramFlavor} is configured for
         * @return the histogram flavor mapped to the {@link Meter.Id} or {@code null} if
         * mapping is undefined
         * @see OtlpConfig#histogramFlavorPerMeter()
         * @see OtlpConfig#histogramFlavor()
         */
        @Nullable HistogramFlavor getHistogramFlavor(Map<String, HistogramFlavor> perMeterMapping, Meter.Id id);

    }

    /**
     * Overridable lookup mechanism for max bucket count. This has no effect on a meter if
     * it does not have an exponential bucket histogram configured.
     */
    // VisibleForTesting
    @FunctionalInterface
    interface MaxBucketsPerMeterLookup {

        /**
         * Default implementation.
         */
        MaxBucketsPerMeterLookup DEFAULT = OtlpMeterRegistry::lookup;

        /**
         * Looks up the max bucket count to use on a per-meter level. This will override
         * the default {@link OtlpConfig#maxBucketCount()} for matching Meters.
         * {@link OtlpConfig#maxBucketsPerMeter()} provides the data while this method
         * provides the logic for the lookup, and you can override them independently.
         * This has no effect on a meter if it does not have an exponential bucket
         * histogram configured.
         * @param perMeterMapping configured mapping data
         * @param id the {@link Meter.Id} the max bucket count is configured for
         * @return the max bucket count mapped to the {@link Meter.Id} or {@code null} if
         * the mapping is undefined
         * @see OtlpConfig#maxBucketsPerMeter()
         * @see OtlpConfig#maxBucketCount()
         */
        @Nullable Integer getMaxBuckets(Map<String, Integer> perMeterMapping, Meter.Id id);

    }

    private static <T> @Nullable T lookup(Map<String, T> values, Meter.Id id) {
        if (values.isEmpty()) {
            return null;
        }
        return doLookup(values, id);
    }

    private static <T> @Nullable T doLookup(Map<String, T> values, Meter.Id id) {
        String name = id.getName();
        while (StringUtils.isNotEmpty(name)) {
            T result = values.get(name);
            if (result != null) {
                return result;
            }
            int lastDot = name.lastIndexOf('.');
            name = (lastDot != -1) ? name.substring(0, lastDot) : "";
        }

        return null;
    }

    /**
     * Builder for {@link OtlpMeterRegistry}.
     *
     * @since 1.15.0
     */
    public static class Builder {

        private final OtlpConfig otlpConfig;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        private @Nullable OtlpMetricsSender metricsSender;

        private @Nullable ExemplarContextProvider exemplarContextProvider;

        private Builder(OtlpConfig otlpConfig) {
            this.otlpConfig = otlpConfig;
        }

        /** Override the default clock. */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /** Override the default {@link ThreadFactory}. */
        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        /**
         * Provide your own custom metrics sender. This can be used to send OTLP protobuf
         * format metrics to an OTLP receiver using different transports or senders.
         * @param metricsSender custom metrics sender
         * @return builder
         * @since 1.15.0
         */
        public Builder metricsSender(OtlpMetricsSender metricsSender) {
            this.metricsSender = metricsSender;
            return this;
        }

        public Builder exemplarContextProvider(ExemplarContextProvider exemplarContextProvider) {
            this.exemplarContextProvider = exemplarContextProvider;
            return this;
        }

        public OtlpMeterRegistry build() {
            OtlpMetricsSender sender = this.metricsSender != null ? this.metricsSender
                    : createDefaultSender(otlpConfig);
            return new OtlpMeterRegistry(otlpConfig, clock, threadFactory, sender, exemplarContextProvider);
        }

    }

}
