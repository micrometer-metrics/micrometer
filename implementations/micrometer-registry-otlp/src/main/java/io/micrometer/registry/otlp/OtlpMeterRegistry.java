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
import io.micrometer.core.instrument.Timer;
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
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

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

    private static final double[] EMPTY_SLO_WITH_POSITIVE_INF = new double[] { Double.POSITIVE_INFINITY };

    private static final String TELEMETRY_SDK_NAME = "telemetry.sdk.name";

    private static final String TELEMETRY_SDK_LANGUAGE = "telemetry.sdk.language";

    private static final String TELEMETRY_SDK_VERSION = "telemetry.sdk.version";

    private static final Set<String> RESERVED_RESOURCE_ATTRIBUTES = new HashSet<>(
            Arrays.asList(TELEMETRY_SDK_NAME, TELEMETRY_SDK_LANGUAGE, TELEMETRY_SDK_VERSION));

    private final InternalLogger logger = InternalLoggerFactory.getInstance(OtlpMeterRegistry.class);

    private final OtlpConfig config;

    private final HttpSender httpSender;

    private final Resource resource;

    private final AggregationTemporality aggregationTemporality;

    private final TimeUnit baseTimeUnit;

    private final String userAgentHeader;

    // Time when the last scheduled rollOver has started. Applicable only for delta
    // flavour.
    private volatile long lastMeterRolloverStartTime = -1;

    @Nullable
    private ScheduledExecutorService meterPollingService;

    public OtlpMeterRegistry() {
        this(OtlpConfig.DEFAULT, Clock.SYSTEM);
    }

    public OtlpMeterRegistry(OtlpConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY);
    }

    /**
     * Create an {@code OtlpMeterRegistry} instance.
     * @param config config
     * @param clock clock
     * @param threadFactory thread factory
     * @since 1.14.0
     */
    public OtlpMeterRegistry(OtlpConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionSender());
    }

    // VisibleForTesting
    // not public until we decide what we want to expose in public API
    // HttpSender may not be a good idea if we will support a non-HTTP transport
    OtlpMeterRegistry(OtlpConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpSender) {
        super(config, clock);
        this.config = config;
        this.baseTimeUnit = config.baseTimeUnit();
        this.httpSender = httpSender;
        this.resource = Resource.newBuilder().addAllAttributes(getResourceAttributes()).build();
        this.aggregationTemporality = config.aggregationTemporality();
        this.userAgentHeader = getUserAgentHeader();
        config().namingConvention(NamingConvention.dot);
        start(threadFactory);
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
                    config.aggregationTemporality(), config().namingConvention());
            otlpMetricConverter.addMeters(batch);

            try {
                ExportMetricsServiceRequest request = ExportMetricsServiceRequest.newBuilder()
                    .addResourceMetrics(ResourceMetrics.newBuilder()
                        .setResource(this.resource)
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                            // we don't have instrumentation library/version
                            // attached to meters; leave unknown for now
                            // .setScope(InstrumentationScope.newBuilder().setName("").setVersion("").build())
                            .addAllMetrics(otlpMetricConverter.getAllMetrics())
                            .build())
                        .build())
                    .build();
                HttpSender.Request.Builder httpRequest = this.httpSender.post(this.config.url())
                    .withHeader("User-Agent", this.userAgentHeader)
                    .withContent("application/x-protobuf", request.toByteArray());
                this.config.headers().forEach(httpRequest::withHeader);
                HttpSender.Response response = httpRequest.send();
                if (!response.isSuccessful()) {
                    logger.warn(
                            "Failed to publish metrics (context: {}). Server responded with HTTP status code {} and body {}",
                            getConfigurationContext(), response.code(), response.body());
                }
            }
            catch (Throwable e) {
                logger.warn(String.format("Failed to publish metrics to OTLP receiver (context: %s)",
                        getConfigurationContext()), e);
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
        super.close();
    }

    private boolean shouldPublishDataForLastStep() {
        if (lastMeterRolloverStartTime < 0)
            return false;

        final long lastPublishedStep = getLastScheduledPublishStartTime() / config.step().toMillis();
        final long lastPolledStep = lastMeterRolloverStartTime / config.step().toMillis();
        return lastPublishedStep < lastPolledStep;
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
            .forEach(m -> m.match(gauge -> null, Counter::count, Timer::takeSnapshot, DistributionSummary::takeSnapshot,
                    meter -> null, meter -> null, FunctionCounter::count, FunctionTimer::count, meter -> null));
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
    static KeyValue createKeyValue(String key, String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }

    // VisibleForTesting
    Iterable<KeyValue> getResourceAttributes() {
        boolean serviceNameProvided = false;
        List<KeyValue> attributes = new ArrayList<>();
        attributes.add(createKeyValue(TELEMETRY_SDK_NAME, "io.micrometer"));
        attributes.add(createKeyValue(TELEMETRY_SDK_LANGUAGE, "java"));
        String micrometerCoreVersion = MeterRegistry.class.getPackage().getImplementationVersion();
        if (micrometerCoreVersion != null) {
            attributes.add(createKeyValue(TELEMETRY_SDK_VERSION, micrometerCoreVersion));
        }
        for (Map.Entry<String, String> keyValue : this.config.resourceAttributes().entrySet()) {
            if ("service.name".equals(keyValue.getKey())) {
                serviceNameProvided = true;
            }
            if (RESERVED_RESOURCE_ATTRIBUTES.contains(keyValue.getKey())) {
                logger.warn("Resource attribute {} is reserved and will be ignored", keyValue.getKey());
                continue;
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
        // While publishing to OTLP, we export either Histogram datapoint (Explicit
        // ExponentialBuckets
        // or Exponential) / Summary
        // datapoint. So, we will make the histogram either of them and not both.
        // Though AbstractTimer/Distribution Summary prefers publishing percentiles,
        // exporting of histograms over percentiles is preferred in OTLP.
        if (distributionStatisticConfig.isPublishingHistogram()) {
            if (HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM
                .equals(histogramFlavor(otlpConfig.histogramFlavor(), distributionStatisticConfig))) {
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

    static HistogramFlavor histogramFlavor(HistogramFlavor preferredHistogramFlavor,
            DistributionStatisticConfig distributionStatisticConfig) {

        final double[] serviceLevelObjectiveBoundaries = distributionStatisticConfig
            .getServiceLevelObjectiveBoundaries();
        if (distributionStatisticConfig.isPublishingHistogram()
                && preferredHistogramFlavor == HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM
                && (serviceLevelObjectiveBoundaries == null || serviceLevelObjectiveBoundaries.length == 0)) {
            return HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM;
        }
        return HistogramFlavor.EXPLICIT_BUCKET_HISTOGRAM;
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

    private String getUserAgentHeader() {
        String userAgent = "Micrometer-OTLP-Exporter-Java";
        String version = getClass().getPackage().getImplementationVersion();
        if (version != null) {
            userAgent += "/" + version;
        }
        return userAgent;
    }

}
