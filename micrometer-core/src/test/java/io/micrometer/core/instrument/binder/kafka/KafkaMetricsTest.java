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

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.metrics.stats.Value;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class KafkaMetricsTest {

    private KafkaMetrics kafkaMetrics;

    @AfterEach
    void afterEach() {
        if (kafkaMetrics != null)
            kafkaMetrics.close();
    }

    @Test
    void shouldKeepMetersWhenMetricsDoNotChange() {
        // Given
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a", "b", "c", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            return Collections.singletonMap(metricName, metric);
        };
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);

        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void closeShouldRemoveAllMetersAndShutdownDefaultScheduler() {
        // Given
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a", "b", "c", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            return Collections.singletonMap(metricName, metric);
        };
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(isDefaultMetricsSchedulerThreadAlive()).isTrue();

        kafkaMetrics.close();
        assertThat(registry.getMeters()).isEmpty();
        await().until(() -> !isDefaultMetricsSchedulerThreadAlive());
    }

    @Test
    void closeShouldRemoveAllMetersAndNotShutdownCustomScheduler() {
        // Given
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a", "b", "c", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            return Collections.singletonMap(metricName, metric);
        };
        ScheduledExecutorService customScheduler = Executors.newScheduledThreadPool(1);
        kafkaMetrics = new KafkaMetrics(supplier, Collections.emptyList(), customScheduler);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);
        await().until(() -> !isDefaultMetricsSchedulerThreadAlive());

        kafkaMetrics.close();
        assertThat(registry.getMeters()).isEmpty();
        assertThat(customScheduler.isShutdown()).isFalse();

        customScheduler.shutdownNow();
        assertThat(customScheduler.isShutdown()).isTrue();
    }

    @Test
    void shouldAddNewMetersWhenMetricsChange() {
        // Given
        AtomicReference<Map<MetricName, KafkaMetric>> metrics = new AtomicReference<>(new LinkedHashMap<>());
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> metrics.updateAndGet(map -> {
            MetricName metricName = new MetricName("a0", "b0", "c0", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            map.put(metricName, metric);
            return map;
        });
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);

        metrics.updateAndGet(map -> {
            MetricName metricName = new MetricName("a1", "b1", "c1", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            map.put(metricName, metric);
            return map;
        });
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(2);
    }

    @Test
    void shouldNotAddAppInfoMetrics() {
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            Map<MetricName, KafkaMetric> metrics = new LinkedHashMap<>();
            MetricName metricName = new MetricName("a0", "b0", "c0", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            metrics.put(metricName, metric);
            MetricName appInfoMetricName = new MetricName("a1", KafkaMetrics.METRIC_GROUP_APP_INFO, "c0",
                    new LinkedHashMap<>());
            KafkaMetric appInfoMetric = new KafkaMetric(this, appInfoMetricName, new Value(), new MetricConfig(),
                    Time.SYSTEM);
            metrics.put(appInfoMetricName, appInfoMetric);
            return metrics;
        };
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);

        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void shouldRemoveOlderMeterWithLessTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a", "b", "c", tags);
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            return Collections.singletonMap(metricName, metric);
        };
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(1); // only
                                                                              // version

        tags.put("key0", "value0");
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(2);
    }

    @Test
    void shouldRemoveMeterWithLessTags() {
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName firstName = new MetricName("a", "b", "c", Collections.emptyMap());
            KafkaMetric firstMetric = new KafkaMetric(this, firstName, new Value(), new MetricConfig(), Time.SYSTEM);
            Map<String, String> tags = new LinkedHashMap<>();
            tags.put("key0", "value0");
            MetricName secondName = new MetricName("a", "b", "c", tags);
            KafkaMetric secondMetric = new KafkaMetric(this, secondName, new Value(), new MetricConfig(), Time.SYSTEM);
            Map<MetricName, KafkaMetric> metrics = new LinkedHashMap<>();
            metrics.put(firstName, firstMetric);
            metrics.put(secondName, secondMetric);
            return metrics;
        };
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(2); // version +
                                                                              // key0
    }

    @Test
    void shouldBindMetersWithSameTags() {
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            Map<String, String> firstTags = new LinkedHashMap<>();
            firstTags.put("key0", "value0");
            MetricName firstName = new MetricName("a", "b", "c", firstTags);
            KafkaMetric firstMetric = new KafkaMetric(this, firstName, new Value(), new MetricConfig(), Time.SYSTEM);
            Map<String, String> secondTags = new LinkedHashMap<>();
            secondTags.put("key0", "value1");
            MetricName secondName = new MetricName("a", "b", "c", secondTags);
            KafkaMetric secondMetric = new KafkaMetric(this, secondName, new Value(), new MetricConfig(), Time.SYSTEM);

            Map<MetricName, KafkaMetric> metrics = new LinkedHashMap<>();
            metrics.put(firstName, firstMetric);
            metrics.put(secondName, secondMetric);
            return metrics;
        };

        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(2);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(2); // version +
                                                                              // key0
    }

    @Issue("#1968")
    @Test
    void shouldBindMetersWithDifferentClientIds() {
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            Map<String, String> firstTags = new LinkedHashMap<>();
            firstTags.put("key0", "value0");
            firstTags.put("client-id", "client0");
            MetricName firstName = new MetricName("a", "b", "c", firstTags);
            KafkaMetric firstMetric = new KafkaMetric(this, firstName, new Value(), new MetricConfig(), Time.SYSTEM);
            return Collections.singletonMap(firstName, firstMetric);
        };

        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("kafka.b.a", "client-id", "client1", "key0", "value0", "kafka-version", "unknown");
        // When
        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(2);
    }

    @Issue("#1968")
    @Test
    void shouldRemoveOlderMeterWithLessTagsWhenCommonTagsConfigured() {
        // Given
        Map<String, String> tags = new LinkedHashMap<>();
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a", "b", "c", tags);
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            return Collections.singletonMap(metricName, metric);
        };

        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.config().commonTags("common", "value");

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags())
            .containsExactlyInAnyOrder(Tag.of("kafka.version", "unknown"), Tag.of("common", "value")); // only
                                                                                                       // version

        tags.put("key0", "value0");
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).containsExactlyInAnyOrder(
                Tag.of("kafka.version", "unknown"), Tag.of("key0", "value0"), Tag.of("common", "value"));
    }

    @Issue("#2212")
    @Test
    void shouldRemoveMeterWithLessTagsWithMultipleClients() {
        // Given
        AtomicReference<Map<MetricName, KafkaMetric>> metrics = new AtomicReference<>(new LinkedHashMap<>());
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> metrics.updateAndGet(map -> {
            Map<String, String> firstTags = new LinkedHashMap<>();
            Map<String, String> secondTags = new LinkedHashMap<>();
            firstTags.put("key0", "value0");
            firstTags.put("client-id", "client0");
            secondTags.put("key0", "value0");
            secondTags.put("client-id", "client1");
            MetricName firstName = new MetricName("a", "b", "c", firstTags);
            MetricName secondName = new MetricName("a", "b", "c", secondTags);
            KafkaMetric firstMetric = new KafkaMetric(this, firstName, new Value(), new MetricConfig(), Time.SYSTEM);
            KafkaMetric secondMetric = new KafkaMetric(this, secondName, new Value(), new MetricConfig(), Time.SYSTEM);

            map.put(firstName, firstMetric);
            map.put(secondName, secondMetric);
            return map;
        });
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();
        // simulate PrometheusMeterRegistry restriction
        registry.config()
            .onMeterAdded(meter -> registry.find(meter.getId().getName())
                .meters()
                .stream()
                .filter(m -> m.getId().getTags().size() != meter.getId().getTags().size())
                .findAny()
                .ifPresent(m -> {
                    throw new RuntimeException("meter exists with different number of tags");
                }));
        // When
        kafkaMetrics.bindTo(registry);
        // Then
        assertThat(registry.getMeters()).hasSize(2);
        // Given
        metrics.updateAndGet(map -> {
            Map<String, String> firstTags = new LinkedHashMap<>();
            Map<String, String> secondTags = new LinkedHashMap<>();
            firstTags.put("key0", "value0");
            firstTags.put("client-id", "client0");
            secondTags.put("key0", "value0");
            secondTags.put("client-id", "client1");
            // more tags than before
            firstTags.put("key1", "value1");
            secondTags.put("key1", "value1");

            MetricName firstName = new MetricName("a", "b", "c", firstTags);
            MetricName secondName = new MetricName("a", "b", "c", secondTags);
            KafkaMetric firstMetric = new KafkaMetric(this, firstName, new Value(), new MetricConfig(), Time.SYSTEM);
            KafkaMetric secondMetric = new KafkaMetric(this, secondName, new Value(), new MetricConfig(), Time.SYSTEM);

            map.put(firstName, firstMetric);
            map.put(secondName, secondMetric);
            return map;
        });
        // When
        kafkaMetrics.checkAndBindMetrics(registry);
        // Then
        assertThat(registry.getMeters()).hasSize(2);
        registry.getMeters()
            .forEach(meter -> assertThat(meter.getId().getTags()).extracting(Tag::getKey)
                .containsOnly("key0", "key1", "client.id", "kafka.version"));
    }

    @Issue("#2726")
    @Test
    void shouldUseMetricFromSupplierIfInstanceChanges() {
        MetricName metricName = new MetricName("a0", "b0", "c0", new LinkedHashMap<>());
        Value oldValue = new Value();
        oldValue.record(new MetricConfig(), 1.0, System.currentTimeMillis());
        KafkaMetric oldMetricInstance = new KafkaMetric(this, metricName, oldValue, new MetricConfig(), Time.SYSTEM);

        Map<MetricName, KafkaMetric> metrics = new HashMap<>();
        metrics.put(metricName, oldMetricInstance);

        kafkaMetrics = new KafkaMetrics(() -> metrics);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);

        Value newValue = new Value();
        newValue.record(new MetricConfig(), 2.0, System.currentTimeMillis());
        KafkaMetric newMetricInstance = new KafkaMetric(this, metricName, newValue, new MetricConfig(), Time.SYSTEM);
        metrics.put(metricName, newMetricInstance);

        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).singleElement()
            .extracting(Meter::measure)
            .satisfies(measurements -> assertThat(measurements).singleElement()
                .extracting(Measurement::getValue)
                .isEqualTo(2.0));
    }

    @Issue("#2801")
    @Test
    void shouldUseMetricFromSupplierIndirectly() {
        AtomicReference<Map<MetricName, KafkaMetric>> metricsReference = new AtomicReference<>(new HashMap<>());

        MetricName oldMetricName = new MetricName("a0", "b0", "c0", new LinkedHashMap<>());
        Value oldValue = new Value();
        oldValue.record(new MetricConfig(), 1.0, System.currentTimeMillis());
        KafkaMetric oldMetricInstance = new KafkaMetric(this, oldMetricName, oldValue, new MetricConfig(), Time.SYSTEM);

        metricsReference.get().put(oldMetricName, oldMetricInstance);

        kafkaMetrics = new KafkaMetrics(metricsReference::get);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);

        assertThat(registry.getMeters()).singleElement()
            .extracting(Meter::measure)
            .satisfies(measurements -> assertThat(measurements).singleElement()
                .extracting(Measurement::getValue)
                .isEqualTo(1.0));

        metricsReference.set(new HashMap<>());
        MetricName newMetricName = new MetricName("a0", "b0", "c0", new LinkedHashMap<>());
        Value newValue = new Value();
        newValue.record(new MetricConfig(), 2.0, System.currentTimeMillis());
        KafkaMetric newMetricInstance = new KafkaMetric(this, newMetricName, newValue, new MetricConfig(), Time.SYSTEM);
        metricsReference.get().put(newMetricName, newMetricInstance);

        assertThat(registry.getMeters()).singleElement()
            .extracting(Meter::measure)
            .satisfies(measurements -> assertThat(measurements).singleElement()
                .extracting(Measurement::getValue)
                .isEqualTo(1.0)); // still referencing the old value since the map
                                  // is only updated in checkAndBindMetrics

        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).singleElement()
            .extracting(Meter::measure)
            .satisfies(measurements -> assertThat(measurements).singleElement()
                .extracting(Measurement::getValue)
                .isEqualTo(2.0)); // referencing the new value since the map was
                                  // updated in checkAndBindMetrics
    }

    @Issue("#2843")
    @Test
    void shouldRemoveOldMeters() {
        Map<MetricName, Metric> kafkaMetricMap = new HashMap<>();
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> kafkaMetricMap;
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.config().commonTags("commonTest", "42");
        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(0);

        MetricName aMetric = createMetricName("a");
        MetricName bMetric = createMetricName("b");

        kafkaMetricMap.put(aMetric, createKafkaMetric(aMetric));
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(1);

        kafkaMetricMap.clear();
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(0);

        kafkaMetricMap.put(aMetric, createKafkaMetric(aMetric));
        kafkaMetricMap.put(bMetric, createKafkaMetric(bMetric));
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(2);

        kafkaMetricMap.clear();
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(0);

        kafkaMetricMap.put(aMetric, createKafkaMetric(aMetric));
        kafkaMetricMap.put(bMetric, createKafkaMetric(bMetric));
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(2);

        kafkaMetricMap.remove(bMetric);
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getName()).isEqualTo("kafka.test.a");

        kafkaMetricMap.clear();
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(0);
    }

    @Issue("#2843")
    @Test
    void shouldRemoveOldMetersWithTags() {
        Map<MetricName, Metric> kafkaMetricMap = new HashMap<>();
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> kafkaMetricMap;
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.config().commonTags("commonTest", "42");
        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(0);

        MetricName aMetricV1 = createMetricName("a", "foo", "v1");
        MetricName aMetricV2 = createMetricName("a", "foo", "v2");
        MetricName bMetric = createMetricName("b", "foo", "n/a");

        kafkaMetricMap.put(aMetricV1, createKafkaMetric(aMetricV1));
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(1);

        kafkaMetricMap.clear();
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(0);

        kafkaMetricMap.put(aMetricV1, createKafkaMetric(aMetricV1));
        kafkaMetricMap.put(aMetricV2, createKafkaMetric(aMetricV2));
        kafkaMetricMap.put(bMetric, createKafkaMetric(bMetric));
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(3);

        kafkaMetricMap.clear();
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(0);

        kafkaMetricMap.put(aMetricV1, createKafkaMetric(aMetricV1));
        kafkaMetricMap.put(aMetricV2, createKafkaMetric(aMetricV2));
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(2);

        kafkaMetricMap.remove(aMetricV1);
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getName()).isEqualTo("kafka.test.a");
        assertThat(registry.getMeters().get(0).getId().getTags()).containsExactlyInAnyOrder(Tag.of("commonTest", "42"),
                Tag.of("kafka.version", "unknown"), Tag.of("foo", "v2"));

        kafkaMetricMap.clear();
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(0);
    }

    @Issue("#2879")
    @Test
    void removeShouldWorkForNonExistingMeters() {
        Map<MetricName, Metric> kafkaMetricMap = new HashMap<>();
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> kafkaMetricMap;
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();
        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(0);

        MetricName aMetric = createMetricName("a");
        kafkaMetricMap.put(aMetric, createKafkaMetric(aMetric));
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(1);

        kafkaMetricMap.clear();
        registry.forEachMeter(registry::remove);
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(0);
    }

    @Issue("#2879")
    @Test
    void checkAndBindMetricsShouldNotFail() {
        kafkaMetrics = new KafkaMetrics(() -> {
            throw new RuntimeException("simulated");
        });
        MeterRegistry registry = new SimpleMeterRegistry();
        kafkaMetrics.checkAndBindMetrics(registry);
    }

    private MetricName createMetricName(String name) {
        return createMetricName(name, Collections.emptyMap());
    }

    private MetricName createMetricName(String name, String... keyValues) {
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }
        else {
            Map<String, String> tagsMap = new HashMap<>();
            for (int i = 0; i < keyValues.length; i += 2) {
                tagsMap.put(keyValues[i], keyValues[i + 1]);
            }

            return createMetricName(name, tagsMap);
        }
    }

    private MetricName createMetricName(String name, Map<String, String> tags) {
        return new MetricName(name, "test", "for testing", tags);
    }

    private KafkaMetric createKafkaMetric(MetricName metricName) {
        return new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
    }

    private static boolean isDefaultMetricsSchedulerThreadAlive() {
        return Thread.getAllStackTraces()
            .keySet()
            .stream()
            .filter(Thread::isAlive)
            .map(Thread::getName)
            .anyMatch(name -> name.startsWith("micrometer-kafka-metrics"));
    }

}
