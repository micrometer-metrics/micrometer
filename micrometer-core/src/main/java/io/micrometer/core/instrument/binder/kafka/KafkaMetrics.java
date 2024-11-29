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

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.Measurable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.Meter.Type.OTHER;
import static java.util.Collections.emptyList;

/**
 * Kafka metrics binder. This should be closed on application shutdown to clean up
 * resources.
 *
 * @author Jorge Quilcate
 * @see <a href="https://docs.confluent.io/current/kafka/monitoring.html">Kakfa monitoring
 * documentation</a>
 * @since 1.4.0
 */
@Incubating(since = "1.4.0")
@NonNullApi
@NonNullFields
class KafkaMetrics implements MeterBinder, AutoCloseable {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(KafkaMetrics.class);

    private static final WarnThenDebugLogger warnThenDebugLogger = new WarnThenDebugLogger(KafkaMetrics.class);

    static final String METRIC_NAME_PREFIX = "kafka.";
    static final String METRIC_GROUP_APP_INFO = "app-info";
    static final String METRIC_GROUP_METRICS_COUNT = "kafka-metrics-count";
    static final String VERSION_METRIC_NAME = "version";
    static final String START_TIME_METRIC_NAME = "start-time-ms";
    static final String KAFKA_VERSION_TAG_NAME = "kafka.version";
    static final String DEFAULT_VALUE = "unknown";

    private static final long REFRESH_INTERVAL_MILLIS = Duration.ofSeconds(60).toMillis();

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

    private final Supplier<Map<MetricName, ? extends Metric>> metricsSupplier;

    private final AtomicReference<Map<MetricName, ? extends Metric>> metrics = new AtomicReference<>();

    private final Iterable<Tag> extraTags;

    private final ScheduledExecutorService scheduler;

    private final boolean schedulerExternallyManaged;

    @Nullable
    private Iterable<Tag> commonTags;

    /**
     * Keeps track of current set of metrics.
     */
    private volatile Set<MetricName> currentMeters = new HashSet<>();

    private String kafkaVersion = DEFAULT_VALUE;

    @Nullable
    private volatile MeterRegistry registry;

    private final Set<Meter.Id> registeredMeterIds = ConcurrentHashMap.newKeySet();

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier) {
        this(metricsSupplier, emptyList());
    }

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier, Iterable<Tag> extraTags) {
        this(metricsSupplier, extraTags, createDefaultScheduler(), false);
    }

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier, Iterable<Tag> extraTags,
            ScheduledExecutorService scheduler) {
        this(metricsSupplier, extraTags, scheduler, true);
    }

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier, Iterable<Tag> extraTags,
            ScheduledExecutorService scheduler, boolean schedulerExternallyManaged) {
        this.metricsSupplier = metricsSupplier;
        this.extraTags = extraTags;
        this.scheduler = scheduler;
        this.schedulerExternallyManaged = schedulerExternallyManaged;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;

        commonTags = getCommonTags(registry);
        prepareToBindMetrics(registry);
        checkAndBindMetrics(registry);
        scheduler.scheduleAtFixedRate(() -> checkAndBindMetrics(registry), REFRESH_INTERVAL_MILLIS,
                REFRESH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private Iterable<Tag> getCommonTags(MeterRegistry registry) {
        // FIXME hack until we have proper API to retrieve common tags
        Meter.Id dummyId = Meter.builder("delete.this", OTHER, Collections.emptyList()).register(registry).getId();
        registry.remove(dummyId);
        return dummyId.getTags();
    }

    /**
     * Define common tags and meters before binding metrics
     */
    void prepareToBindMetrics(MeterRegistry registry) {
        this.metrics.set(this.metricsSupplier.get());
        Map<MetricName, ? extends Metric> metrics = this.metrics.get();
        // Collect static metrics and tags
        Metric startTimeMetric = null;

        for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
            MetricName name = entry.getKey();
            if (METRIC_GROUP_APP_INFO.equals(name.group()))
                if (VERSION_METRIC_NAME.equals(name.name())) {
                    kafkaVersion = (String) entry.getValue().metricValue();
                }
                else if (START_TIME_METRIC_NAME.equals(name.name())) {
                    startTimeMetric = entry.getValue();
                }
        }

        if (startTimeMetric != null) {
            MetricName startTimeMetricName = startTimeMetric.metricName();
            bindMeter(registry, startTimeMetric, meterName(startTimeMetricName), meterTags(startTimeMetricName));
        }
    }

    /**
     * Gather metrics from Kafka metrics API and register Meters.
     * <p>
     * As this is a one-off execution when binding a Kafka client, Meters include a call
     * to this validation to double-check new metrics when returning values. This should
     * only add the cost of comparing meters last returned from the Kafka client.
     */
    void checkAndBindMetrics(MeterRegistry registry) {
        try {
            Map<MetricName, ? extends Metric> currentMetrics = this.metricsSupplier.get();
            this.metrics.set(currentMetrics);

            if (!currentMeters.equals(currentMetrics.keySet())) {
                Set<MetricName> metricsToRemove = currentMeters.stream()
                    .filter(metricName -> !currentMetrics.containsKey(metricName))
                    .collect(Collectors.toSet());

                for (MetricName metricName : metricsToRemove) {
                    Meter.Id id = meterIdForComparison(metricName);
                    registry.remove(id);
                    registeredMeterIds.remove(id);
                }

                currentMeters = new HashSet<>(currentMetrics.keySet());

                Map<String, List<Meter>> registryMetersByNames = registry.getMeters()
                    .stream()
                    .collect(Collectors.groupingBy(meter -> meter.getId().getName()));

                currentMetrics.forEach((name, metric) -> {
                    // Filter out non-numeric values
                    // Filter out metrics from groups that include metadata
                    if (!(metric.metricValue() instanceof Number) || METRIC_GROUP_APP_INFO.equals(name.group())
                            || METRIC_GROUP_METRICS_COUNT.equals(name.group())) {
                        return;
                    }

                    String meterName = meterName(name);

                    // Kafka has metrics with lower number of tags (e.g. with/without
                    // topic or partition tag)
                    // Remove meters with lower number of tags
                    boolean hasLessTags = false;
                    for (Meter other : registryMetersByNames.getOrDefault(meterName, emptyList())) {
                        Meter.Id otherId = other.getId();
                        List<Tag> tags = otherId.getTags();
                        List<Tag> meterTagsWithCommonTags = meterTags(name, true);
                        if (tags.size() < meterTagsWithCommonTags.size()) {
                            registry.remove(otherId);
                            registeredMeterIds.remove(otherId);
                        }
                        // Check if already exists
                        else if (tags.size() == meterTagsWithCommonTags.size())
                            if (tags.containsAll(meterTagsWithCommonTags))
                                return;
                            else
                                break;
                        else
                            hasLessTags = true;
                    }
                    if (hasLessTags)
                        return;

                    List<Tag> tags = meterTags(name);
                    try {
                        Meter meter = bindMeter(registry, metric, meterName, tags);
                        List<Meter> meters = registryMetersByNames.computeIfAbsent(meterName, k -> new ArrayList<>());
                        meters.add(meter);
                    }
                    catch (Exception ex) {
                        String message = ex.getMessage();
                        if (message != null && message.contains("Prometheus requires")) {
                            warnThenDebugLogger.log(() -> "Failed to bind meter: " + meterName + " " + tags
                                    + ". However, this could happen and might be restored in the next refresh.");
                        }
                        else {
                            log.warn("Failed to bind meter: " + meterName + " " + tags + ".", ex);
                        }
                    }
                });
            }
        }
        catch (Exception e) {
            log.warn("Failed to bind KafkaMetric", e);
        }
    }

    private Meter bindMeter(MeterRegistry registry, Metric metric, String meterName, Iterable<Tag> tags) {
        Meter meter = registerMeter(registry, metric, meterName, tags);
        registeredMeterIds.add(meter.getId());
        return meter;
    }

    private Meter registerMeter(MeterRegistry registry, Metric metric, String meterName, Iterable<Tag> tags) {
        MetricName metricName = metric.metricName();
        Class<? extends Measurable> measurableClass = getMeasurableClass(metric);
        if ((measurableClass == null && meterName.endsWith("total"))
                || (measurableClass != null && counterMeasurableClasses.contains(measurableClass))) {
            return registerCounter(registry, metricName, meterName, tags);
        }

        return registerGauge(registry, metricName, meterName, tags);
    }

    @Nullable
    private static Class<? extends Measurable> getMeasurableClass(Metric metric) {
        if (!(metric instanceof KafkaMetric)) {
            return null;
        }

        try {
            return ((KafkaMetric) metric).measurable().getClass();
        }
        catch (IllegalStateException ex) {
            return null;
        }
    }

    private static ScheduledExecutorService createDefaultScheduler() {
        return Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("micrometer-kafka-metrics"));
    }

    private Gauge registerGauge(MeterRegistry registry, MetricName metricName, String meterName, Iterable<Tag> tags) {
        return Gauge.builder(meterName, this.metrics, toMetricValue(metricName))
            .tags(tags)
            .description(metricName.description())
            .register(registry);
    }

    private FunctionCounter registerCounter(MeterRegistry registry, MetricName metricName, String meterName,
            Iterable<Tag> tags) {
        return FunctionCounter.builder(meterName, this.metrics, toMetricValue(metricName))
            .tags(tags)
            .description(metricName.description())
            .register(registry);
    }

    private ToDoubleFunction<AtomicReference<Map<MetricName, ? extends Metric>>> toMetricValue(MetricName metricName) {
        return metricsReference -> toDouble(metricsReference.get().get(metricName));
    }

    private double toDouble(@Nullable Metric metric) {
        return (metric != null) ? ((Number) metric.metricValue()).doubleValue() : Double.NaN;
    }

    private List<Tag> meterTags(MetricName metricName, boolean includeCommonTags) {
        List<Tag> tags = new ArrayList<>();
        metricName.tags().forEach((key, value) -> tags.add(Tag.of(key.replaceAll("-", "."), value)));
        tags.add(Tag.of(KAFKA_VERSION_TAG_NAME, kafkaVersion));
        extraTags.forEach(tags::add);
        if (includeCommonTags) {
            commonTags.forEach(tags::add);
        }
        return tags;
    }

    private List<Tag> meterTags(MetricName metricName) {
        return meterTags(metricName, false);
    }

    private String meterName(MetricName metricName) {
        String name = METRIC_NAME_PREFIX + metricName.group() + "." + metricName.name();
        return name.replaceAll("-metrics", "").replaceAll("-", ".");
    }

    private Meter.Id meterIdForComparison(MetricName metricName) {
        return new Meter.Id(meterName(metricName), Tags.of(meterTags(metricName, true)), null, null, OTHER);
    }

    @Override
    public void close() {
        if (!schedulerExternallyManaged) {
            this.scheduler.shutdownNow();
        }

        for (Meter.Id id : registeredMeterIds) {
            registry.remove(id);
        }
    }

}
