/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.*;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Measurable;
import org.apache.kafka.common.metrics.MetricsContext;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static io.micrometer.core.instrument.Meter.Type.OTHER;

public class KafkaMetricsReporter implements MetricsReporter {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(KafkaMetricsReporter.class);

    private static final WarnThenDebugLogger warnThenDebugLogger = new WarnThenDebugLogger(KafkaMetricsReporter.class);

    static final String METRIC_NAME_PREFIX = "kafka.";
    static final String METRIC_GROUP_APP_INFO = "app-info";
    static final String METRIC_GROUP_METRICS_COUNT = "kafka-metrics-count";
    static final String VERSION_METRIC_NAME = "version";
    static final String START_TIME_METRIC_NAME = "start-time-ms";
    static final String KAFKA_VERSION_TAG_NAME = "kafka.version";
    static final String DEFAULT_VALUE = "unknown";

    private static final Set<Class<?>> counterMeasurableClasses = new HashSet<>();

    static {
        Set<String> classNames = new HashSet<>();
        classNames.add("org.apache.kafka.common.metrics.stats.CumulativeSum");
        classNames.add("org.apache.kafka.common.metrics.stats.CumulativeCount");

        for (String className : classNames) {
            try {
                counterMeasurableClasses.add(Class.forName(className));
            }
            catch (ClassNotFoundException e) {
                // Class doesn't exist in this version of kafka client - skip
            }
        }
    }

    private static final AtomicReference<MeterRegistry> DEFAULT_REGISTRY = new AtomicReference<>();

    private final Map<MetricName, KafkaMetric> metrics = new ConcurrentHashMap<>();

    private final Set<Meter.Id> registeredMeterIds = ConcurrentHashMap.newKeySet();

    private String kafkaVersion = DEFAULT_VALUE;

    private @Nullable MeterRegistry registry;

    /**
     * Sets the default MeterRegistry to be used by all KafkaMetricsReporter instances.
     *
     * <p>
     * This method must be called before creating any Kafka clients that use this
     * reporter.
     * @param registry the MeterRegistry to use
     */
    public static void setDefaultMeterRegistry(MeterRegistry registry) {
        DEFAULT_REGISTRY.set(registry);
    }

    /**
     * Returns the default MeterRegistry.
     * @return the default MeterRegistry, or null if not set
     */
    public static @Nullable MeterRegistry getDefaultMeterRegistry() {
        return DEFAULT_REGISTRY.get();
    }

    /**
     * Called by Kafka to configure this reporter with client properties.
     * @param configs the Kafka client configuration
     */
    @Override
    public void configure(Map<String, ?> configs) {
        // Get MeterRegistry
        this.registry = DEFAULT_REGISTRY.get();
        if (this.registry == null) {
            // Fallback to global registry
            this.registry = Metrics.globalRegistry;
            log.warn("No MeterRegistry set via setDefaultMeterRegistry(). Using global registry.");
        }
    }

    /**
     * Called by Kafka with all existing metrics when the reporter is initialized.
     * @param metrics list of existing Kafka metrics
     */
    @Override
    public void init(List<KafkaMetric> metrics) {
        // First pass: extract version and start-time from app-info group
        for (KafkaMetric metric : metrics) {
            MetricName name = metric.metricName();
            if (METRIC_GROUP_APP_INFO.equals(name.group())) {
                if (VERSION_METRIC_NAME.equals(name.name())) {
                    Object value = metric.metricValue();
                    if (value instanceof String) {
                        this.kafkaVersion = (String) value;
                    }
                }
                else if (START_TIME_METRIC_NAME.equals(name.name())) {
                    registerMetric(metric);
                }
            }
        }

        // Second pass: register all other metrics
        for (KafkaMetric metric : metrics) {
            MetricName name = metric.metricName();
            if (!METRIC_GROUP_APP_INFO.equals(name.group())
                    || (!VERSION_METRIC_NAME.equals(name.name()) && !START_TIME_METRIC_NAME.equals(name.name()))) {
                registerMetric(metric);
            }
        }
    }

    /**
     * Called by Kafka when a metric is added or updated.
     * @param metric the added or updated metric
     */
    @Override
    public void metricChange(KafkaMetric metric) {
        MetricName name = metric.metricName();

        // Update version if app-info version metric
        if (METRIC_GROUP_APP_INFO.equals(name.group()) && VERSION_METRIC_NAME.equals(name.name())) {
            Object value = metric.metricValue();
            if (value instanceof String) {
                this.kafkaVersion = (String) value;
            }
            return;
        }

        registerMetric(metric);
    }

    /**
     * Called by Kafka when a metric is removed.
     * @param metric the removed metric
     */
    @Override
    public void metricRemoval(KafkaMetric metric) {
        MetricName metricName = metric.metricName();

        // Remove from our metrics map
        metrics.remove(metricName);

        // Remove from registry
        if (registry != null) {
            Meter.Id id = meterIdForComparison(metricName);
            registry.remove(id);
            registeredMeterIds.remove(id);
        }
    }

    /**
     * Called by Kafka when the reporter is closed.
     */
    @Override
    public void close() {
        if (registry != null) {
            for (Meter.Id id : registeredMeterIds) {
                registry.remove(id);
            }
        }
        registeredMeterIds.clear();
        metrics.clear();
    }

    @Override
    public void contextChange(MetricsContext metricsContext) {
        // No op
    }

    private void registerMetric(KafkaMetric metric) {
        if (registry == null) {
            return;
        }

        MetricName metricName = metric.metricName();

        // Filter out non-numeric values
        if (!(metric.metricValue() instanceof Number)) {
            return;
        }

        // Filter out metadata groups
        if (METRIC_GROUP_APP_INFO.equals(metricName.group()) && !START_TIME_METRIC_NAME.equals(metricName.name())) {
            return;
        }
        if (METRIC_GROUP_METRICS_COUNT.equals(metricName.group())) {
            return;
        }

        // Check if already registered
        Meter.Id existingId = meterIdForComparison(metricName);
        if (registeredMeterIds.contains(existingId)) {
            // Update the metric reference
            metrics.put(metricName, metric);
            return;
        }

        // Handle tag count comparison (Kafka has metrics with varying tag counts)
        String meterName = meterName(metricName);
        List<Tag> meterTagsList = meterTags(metricName);

        for (Meter existingMeter : registry.getMeters()) {
            if (existingMeter.getId().getName().equals(meterName)) {
                List<Tag> existingTags = existingMeter.getId().getTags();

                if (existingTags.size() < meterTagsList.size()) {
                    // Remove meter with fewer tags
                    registry.remove(existingMeter.getId());
                    registeredMeterIds.remove(existingMeter.getId());
                }
                else if (existingTags.size() > meterTagsList.size()) {
                    // Don't register this metric as there's one with more tags
                    return;
                }
                else if (new HashSet<>(existingTags).containsAll(meterTagsList)) {
                    // Already exists with same tags
                    metrics.put(metricName, metric);
                    return;
                }
            }
        }

        // Store metric for value retrieval
        metrics.put(metricName, metric);

        // Register the meter
        try {
            Meter meter = bindMeter(registry, metric, meterName, meterTagsList);
            registeredMeterIds.add(meter.getId());
        }
        catch (Exception ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("Prometheus requires")) {
                warnThenDebugLogger.log(() -> "Failed to bind meter: " + meterName + " " + meterTagsList
                        + ". However, this could happen and might be restored in the next refresh.");
            }
            else {
                log.warn("Failed to bind meter: " + meterName + " " + meterTagsList + ".", ex);
            }
        }
    }

    private Meter bindMeter(MeterRegistry registry, KafkaMetric metric, String meterName, Iterable<Tag> tags) {
        MetricName metricName = metric.metricName();
        Class<? extends Measurable> measurableClass = getMeasurableClass(metric);

        if ((measurableClass == null && meterName.endsWith("total"))
                || (measurableClass != null && counterMeasurableClasses.contains(measurableClass))) {
            return registerCounter(registry, metricName, meterName, tags);
        }

        return registerGauge(registry, metricName, meterName, tags);
    }

    private Gauge registerGauge(MeterRegistry registry, MetricName metricName, String meterName, Iterable<Tag> tags) {
        return Gauge.builder(meterName, metrics, m -> toDouble(m.get(metricName)))
            .tags(tags)
            .description(metricName.description())
            .register(registry);
    }

    private FunctionCounter registerCounter(MeterRegistry registry, MetricName metricName, String meterName,
            Iterable<Tag> tags) {
        return FunctionCounter.builder(meterName, metrics, m -> toDouble(m.get(metricName)))
            .tags(tags)
            .description(metricName.description())
            .register(registry);
    }

    private double toDouble(@Nullable KafkaMetric metric) {
        if (metric == null) {
            return Double.NaN;
        }
        Object metricValue = metric.metricValue();
        if (metricValue == null) {
            return Double.NaN;
        }
        if (!(metricValue instanceof Number)) {
            return Double.NaN;
        }
        return ((Number) metricValue).doubleValue();
    }

    private static @Nullable Class<? extends Measurable> getMeasurableClass(KafkaMetric metric) {
        try {
            return metric.measurable().getClass();
        }
        catch (IllegalStateException ex) {
            return null;
        }
    }

    private List<Tag> meterTags(MetricName metricName) {
        List<Tag> tags = new ArrayList<>();
        metricName.tags().forEach((key, value) -> tags.add(Tag.of(key.replace('-', '.'), value)));
        tags.add(Tag.of(KAFKA_VERSION_TAG_NAME, kafkaVersion));
        return tags;
    }

    private String meterName(MetricName metricName) {
        String name = METRIC_NAME_PREFIX + metricName.group() + "." + metricName.name();
        return name.replaceAll("-metrics", "").replace('-', '.');
    }

    private Meter.Id meterIdForComparison(MetricName metricName) {
        return new Meter.Id(meterName(metricName), Tags.of(meterTags(metricName)), null, null, OTHER);
    }

}
