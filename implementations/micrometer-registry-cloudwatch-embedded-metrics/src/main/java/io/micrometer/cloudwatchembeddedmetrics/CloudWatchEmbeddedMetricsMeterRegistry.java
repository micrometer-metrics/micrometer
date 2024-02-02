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
package io.micrometer.cloudwatchembeddedmetrics;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.cloudwatchlogs.emf.Constants;
import software.amazon.cloudwatchlogs.emf.config.Configuration;
import software.amazon.cloudwatchlogs.emf.exception.*;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.Unit;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;

import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link StepMeterRegistry} for Amazon CloudWatch Embedded Metrics Format (EMF).
 *
 * @author Kyle Sletmoe
 * @author Dawid Kublik
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Pierre-Yves B.
 * @since 1.14.0
 */
public class CloudWatchEmbeddedMetricsMeterRegistry extends StepMeterRegistry {

    private static final Map<String, Unit> UNIT_BY_LOWERCASE_VALUE;

    static {
        Map<String, Unit> unitByLowercaseValue = new HashMap<>();
        for (Unit unit : Unit.values()) {
            if (unit != Unit.UNKNOWN_TO_SDK_VERSION) {
                unitByLowercaseValue.put(unit.toString().toLowerCase(), unit);
            }
        }
        UNIT_BY_LOWERCASE_VALUE = Collections.unmodifiableMap(unitByLowercaseValue);
    }

    private final CloudWatchEmbeddedMetricsConfig config;

    private final Configuration metricsLoggerConfig;

    private final MetricsLogger metricsLogger;

    private final Logger logger = LoggerFactory.getLogger(CloudWatchEmbeddedMetricsMeterRegistry.class);

    private static final WarnThenDebugLogger warnThenDebugLogger = new WarnThenDebugLogger(
            CloudWatchEmbeddedMetricsMeterRegistry.class);

    public CloudWatchEmbeddedMetricsMeterRegistry(CloudWatchEmbeddedMetricsConfig config, Clock clock) {
        this(config, clock, new NamedThreadFactory("cloudwatch-embedded-metrics-publisher"));
    }

    public CloudWatchEmbeddedMetricsMeterRegistry(CloudWatchEmbeddedMetricsConfig config, Clock clock,
            ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;
        this.metricsLoggerConfig = CloudWatchEmbeddedMetricsMeterRegistry.buildMetricsLoggerConfig(config);

        this.metricsLogger = new MetricsLogger();
        this.metricsLogger.setFlushPreserveDimensions(false); // only default dimensions
                                                              // will remain after a flush
        String namespace = config.namespace();
        if (namespace != null) {
            try {
                this.metricsLogger.setNamespace(namespace);
            }
            catch (InvalidNamespaceException e) {
                logger.warn("Invalid namespace '" + namespace + "'; using default namespace. " + e);
            }
        }

        config().namingConvention(new CloudWatchEmbeddedMetricsNamingConvention());
        start(threadFactory);
    }

    private static Configuration buildMetricsLoggerConfig(CloudWatchEmbeddedMetricsConfig config) {
        Configuration metricsLoggerConfig = new Configuration();

        String logGroupName = config.logGroupName();
        if (logGroupName != null) {
            metricsLoggerConfig.setLogGroupName(logGroupName);
        }

        String logStreamName = config.logStreamName();
        if (logStreamName != null) {
            metricsLoggerConfig.setLogStreamName(logStreamName);
        }

        String serviceType = config.serviceType();
        if (serviceType != null) {
            metricsLoggerConfig.setServiceType(serviceType);
        }

        String serviceName = config.serviceName();
        if (serviceName != null) {
            metricsLoggerConfig.setServiceName(serviceName);
        }

        String agentEndpoint = config.agentEndpoint();
        if (agentEndpoint != null) {
            metricsLoggerConfig.setAgentEndpoint(agentEndpoint);
        }

        metricsLoggerConfig.setAsyncBufferSize(config.asyncBufferSize());

        return metricsLoggerConfig;
    }

    @Override
    protected void publish() {
        boolean interrupted = false;
        Instant timestamp = getTimestamp();
        try {
            sendMetricData(timestamp, metricData());
        }
        catch (InterruptedException ex) {
            interrupted = true;
        }
        finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // VisibleForTesting
    void sendMetricData(Instant timestamp, List<MetricDatum> metricData) throws InterruptedException {
        metricsLogger.resetDimensions(config.useDefaultDimensions());

        try {
            metricsLogger.setTimestamp(timestamp);
        }
        catch (InvalidTimestampException e) {
            logger.warn("Could not set timestamp for metrics step; defaulting to standard timestamp behavior " + e);
        }

        for (MetricDatum metricDatum : metricData) {
            if (metricDatum.dimensions != null) {
                metricsLogger.putDimensions(metricDatum.dimensions);
            }

            try {
                metricsLogger.putMetric(metricDatum.metricName, metricDatum.value, metricDatum.unit,
                        metricDatum.storageResolution);
            }
            catch (InvalidMetricException e) {
                logger.error("Could not publish metric " + metricDatum + "; " + e);
            }
        }

        metricsLogger.flush();
    }

    // VisibleForTesting
    List<MetricDatum> metricData() {
        Step step = new Step();
        // @formatter:off
        return getMeters().stream()
            .flatMap(m -> m.match(
                    step::gaugeData,
                    step::counterData,
                    step::timerData,
                    step::summaryData,
                    step::longTaskTimerData,
                    step::timeGaugeData,
                    step::functionCounterData,
                    step::functionTimerData,
                    step::metricData))
            .collect(toList());
        // @formatter:on
    }

    private Instant getTimestamp() {
        return Instant.ofEpochMilli(clock.wallTime());
    }

    // VisibleForTesting
    class Step {

        private Stream<MetricDatum> gaugeData(Gauge gauge) {
            MetricDatum metricDatum = metricDatum(gauge.getId(), "value", gauge.value());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        private Stream<MetricDatum> counterData(Counter counter) {
            return Stream.of(metricDatum(counter.getId(), "count", Unit.COUNT, counter.count()));
        }

        // VisibleForTesting
        Stream<MetricDatum> timerData(Timer timer) {
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            metrics
                .add(metricDatum(timer.getId(), "sum", getBaseTimeUnit().name(), timer.totalTime(getBaseTimeUnit())));
            long count = timer.count();
            metrics.add(metricDatum(timer.getId(), "count", Unit.COUNT, count));
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
            metrics.add(metricDatum(summary.getId(), "count", Unit.COUNT, count));
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
            MetricDatum metricDatum = metricDatum(counter.getId(), "count", Unit.COUNT, counter.count());
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
            metrics.add(metricDatum(timer.getId(), "count", Unit.COUNT, count));
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
            return metricDatum(id, suffix, toUnit(unit), value);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, Unit unit, double value) {
            if (Double.isNaN(value)) {
                return null;
            }

            List<Tag> tags = id.getConventionTags(config().namingConvention());
            if (tags.size() > Constants.MAX_DIMENSION_SET_SIZE) {
                warnThenDebugLogger.log(() -> "Meter " + id.getName() + " has more tags (" + tags.size()
                        + ") than the max supported by CloudWatch (" + Constants.MAX_DIMENSION_SET_SIZE
                        + "). Some tags will be dropped.");
            }

            return new MetricDatum(getMetricName(id, suffix), toDimensions(tags),
                    CloudWatchUtils.clampMetricValue(value), unit, config.storageResolution());
        }

        // VisibleForTesting
        String getMetricName(Meter.Id id, @Nullable String suffix) {
            String name = suffix != null ? id.getName() + "." + suffix : id.getName();
            return config().namingConvention().name(name, id.getType(), id.getBaseUnit());
        }

        // VisibleForTesting
        Unit toUnit(@Nullable String unit) {
            if (unit == null) {
                return Unit.NONE;
            }
            Unit unitEnum = UNIT_BY_LOWERCASE_VALUE.get(unit.toLowerCase());
            return unitEnum != null ? unitEnum : Unit.NONE;
        }

        private @Nullable DimensionSet toDimensions(List<Tag> tags) {
            DimensionSet dimensionSet = null;

            List<Tag> limitedTags = tags.stream()
                .filter(this::isAcceptableTag)
                .limit(Constants.MAX_DIMENSION_SET_SIZE)
                .collect(toList());

            for (Tag tag : limitedTags) {
                try {
                    if (dimensionSet == null) {
                        dimensionSet = DimensionSet.of(tag.getKey(), tag.getValue());
                    }
                }
                catch (InvalidDimensionException e) {
                    warnThenDebugLogger.log(() -> "Dropping a tag with key '" + tag.getKey() + "' and value '"
                            + tag.getValue() + "' because it is an invalid dimension: " + e);
                }
                catch (DimensionSetExceededException e) {
                    // Even though we limited the number of tags above, if default
                    // dimensions are used, we could
                    // exceed the allowed value. If this happens, set useDefaultDimensions
                    // to false on the
                    // CloudWatchEmbeddedMetricsConfig object.
                    warnThenDebugLogger
                        .log(() -> "Dropping a tag with key '" + tag.getKey() + "' and value '" + tag.getValue()
                                + "' because the maximum number of dimensions is exceeded. You may need to disable "
                                + "default dimensions: " + e);
                }
            }

            return dimensionSet;
        }

        private boolean isAcceptableTag(Tag tag) {
            if (StringUtils.isBlank(tag.getValue())) {
                warnThenDebugLogger
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
