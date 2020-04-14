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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.metrics.stats.Value;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaMetricsTest {

    KafkaMetrics kafkaMetrics;

    @AfterEach
    void afterEach() {
        if (kafkaMetrics != null)
            kafkaMetrics.close();
    }

    @Test void shouldKeepMetersWhenMetricsDoNotChange() {
        //Given
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a", "b", "c", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            return Collections.singletonMap(metricName, metric);
        };
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test void shouldAddNewMetersWhenMetricsChange() {
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
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
        //Given
        metrics.updateAndGet(map -> {
            MetricName metricName = new MetricName("a1", "b1", "c1", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            map.put(metricName, metric);
            return map;
        });
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(2);
    }

    @Test void shouldNotAddAppInfoMetrics() {
        //Given
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
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test void shouldRemoveOlderMeterWithLessTags() {
        //Given
        Map<String, String> tags = new LinkedHashMap<>();
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a", "b", "c", tags);
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), new MetricConfig(), Time.SYSTEM);
            return Collections.singletonMap(metricName, metric);
        };
        kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(1); //only version
        //Given
        tags.put("key0", "value0");
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(2);
    }

    @Test void shouldRemoveMeterWithLessTags() {
        //Given
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
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
        Meter meter = registry.getMeters().get(0);
        assertThat(meter.getId().getTags()).hasSize(2); // version + key0
    }

    @Test void shouldBindMetersWithSameTags() {
        //Given
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
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(2);
        Meter meter = registry.getMeters().get(0);
        assertThat(meter.getId().getTags()).hasSize(2); // version + key0
    }
}
