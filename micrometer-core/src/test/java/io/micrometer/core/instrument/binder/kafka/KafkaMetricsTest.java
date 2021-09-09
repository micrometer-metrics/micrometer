/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaMetricsTest {
    private KafkaMetrics kafkaMetrics;

    @AfterEach
    void afterEach() {
        if (kafkaMetrics != null)
            kafkaMetrics.close();
    }

    @Test
    void shouldKeepMetersWhenMetricsDoNotChange() {
        //Given
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
    void closeShouldRemoveAllMeters() {
        //Given
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a", "b", "c", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            return Collections.singletonMap(metricName, metric);
        };
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);

        kafkaMetrics.close();
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void shouldAddNewMetersWhenMetricsChange() {
        //Given
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
            MetricName appInfoMetricName =
                    new MetricName("a1", KafkaMetrics.METRIC_GROUP_APP_INFO, "c0",
                            new LinkedHashMap<>());
            KafkaMetric appInfoMetric =
                    new KafkaMetric(this, appInfoMetricName, new Value(), new MetricConfig(), Time.SYSTEM);
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
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(1); //only version

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
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(2); // version + key0
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
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(2); // version + key0
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
        //When
        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(2);
    }

    @Issue("#1968")
    @Test
    void shouldRemoveOlderMeterWithLessTagsWhenCommonTagsConfigured() {
        //Given
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
        assertThat(registry.getMeters().get(0).getId().getTags()).containsExactlyInAnyOrder(Tag.of("kafka.version", "unknown"), Tag.of("common", "value")); // only version

        tags.put("key0", "value0");
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).containsExactlyInAnyOrder(Tag.of("kafka.version", "unknown"), Tag.of("key0", "value0"), Tag.of("common", "value"));
    }

    @Issue("#2212")
    @Test
    void shouldRemoveMeterWithLessTagsWithMultipleClients() {
        //Given
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
                .onMeterAdded(meter -> registry.find(meter.getId().getName()).meters().stream()
                        .filter(m -> m.getId().getTags().size() != meter.getId().getTags().size())
                        .findAny().ifPresent(m -> {
                            throw new RuntimeException("meter exists with different number of tags");
                        }));
        //When
        kafkaMetrics.bindTo(registry);
        //Then
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
        registry.getMeters().forEach(meter -> assertThat(meter.getId().getTags())
                .extracting(Tag::getKey).containsOnly("key0", "key1", "client.id", "kafka.version"));
    }

    @Issue("#2726")
    @Test
    void shouldAlwaysUseMetricFromSupplierIfInstanceChanges() {
        //Given
        Map<MetricName, KafkaMetric> metrics = new HashMap<>();
        MetricName metricName = new MetricName("a0", "b0", "c0", new LinkedHashMap<>());
        Value oldValue = new Value();
        KafkaMetric oldMetricInstance = new KafkaMetric(this, metricName, oldValue, new MetricConfig(), Time.SYSTEM);
        oldValue.record(new MetricConfig(), 1.0, System.currentTimeMillis());
        metrics.put(metricName, oldMetricInstance);
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> metrics;
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();

        kafkaMetrics.bindTo(registry);
        assertThat(registry.getMeters()).hasSize(1);

        Value newValue = new Value();
        KafkaMetric newMetricInstance = new KafkaMetric(this, metricName, newValue, new MetricConfig(), Time.SYSTEM);
        newValue.record(new MetricConfig(), 2.0, System.currentTimeMillis());
        metrics.put(metricName, newMetricInstance);
        kafkaMetrics.checkAndBindMetrics(registry);
        assertThat(registry.getMeters()).singleElement().extracting(Meter::measure)
                                        .satisfies(measurements ->
                                                           assertThat(measurements).singleElement().extracting(Measurement::getValue).isEqualTo(2.0));
    }
}
