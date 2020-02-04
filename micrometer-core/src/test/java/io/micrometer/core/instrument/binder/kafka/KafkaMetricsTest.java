/**
 * Copyright 2020 Pivotal Software, Inc.
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.stats.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaMetricsTest {

    @Test void shouldKeepMetersWhenMetricsDoNotChange() {
        //Given
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            Map<MetricName, KafkaMetric> metrics = new LinkedHashMap<>();
            MetricName metricName = new MetricName("a", "b", "c", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), null, null);
            metrics.put(metricName, metric);
            return metrics;
        };
        KafkaMetrics kafkaMetrics = new KafkaMetrics(supplier);
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
        Map<MetricName, KafkaMetric> metrics = new LinkedHashMap<>();
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a0", "b0", "c0", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), null, null);
            metrics.put(metricName, metric);
            return metrics;
        };
        KafkaMetrics kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
        //Given
        MetricName metricName = new MetricName("a1", "b1", "c1", new LinkedHashMap<>());
        KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), null, null);
        metrics.put(metricName, metric);
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(2);
    }

    @Test void shouldNotAddAppInfoMetrics() {
        //Given
        Map<MetricName, KafkaMetric> metrics = new LinkedHashMap<>();
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a0", "b0", "c0", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), null, null);
            metrics.put(metricName, metric);
            MetricName appInfoMetricName =
                    new MetricName("a1", KafkaMetrics.METRIC_GROUP_APP_INFO, "c0",
                            new LinkedHashMap<>());
            KafkaMetric appInfoMetric =
                    new KafkaMetric(this, appInfoMetricName, new Value(), null, null);
            metrics.put(appInfoMetricName, appInfoMetric);
            return metrics;
        };
        KafkaMetrics kafkaMetrics = new KafkaMetrics(supplier);
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

    @Test void shouldRemoveMeterWithLessTags() {
        //Given
        Map<MetricName, KafkaMetric> metrics = new LinkedHashMap<>();
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            MetricName metricName = new MetricName("a", "b", "c", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), null, null);
            metrics.put(metricName, metric);
            return metrics;
        };
        KafkaMetrics kafkaMetrics = new KafkaMetrics(supplier);
        MeterRegistry registry = new SimpleMeterRegistry();
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(0);
        //Given
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("key0", "value0");
        MetricName metricName = new MetricName("a", "b", "c", tags);
        KafkaMetric metric = new KafkaMetric(this, metricName, new Value(), null, null);
        metrics.put(metricName, metric);
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(1);
    }
}
