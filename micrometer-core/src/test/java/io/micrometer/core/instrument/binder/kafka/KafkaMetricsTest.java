package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.stats.Count;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaMetricsTest {

    @Test void shouldKeepMetersWhenMetricsDoNotChange() {
        //Given
        Supplier<Map<MetricName, ? extends Metric>> supplier = () -> {
            Map<MetricName, KafkaMetric> metrics = new LinkedHashMap<>();
            MetricName metricName = new MetricName("a", "b", "c", new LinkedHashMap<>());
            KafkaMetric metric = new KafkaMetric(this, metricName, new Count(), null, null);
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
            KafkaMetric metric = new KafkaMetric(this, metricName, new Count(), null, null);
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
        KafkaMetric metric = new KafkaMetric(this, metricName, new Count(), null, null);
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
            KafkaMetric metric = new KafkaMetric(this, metricName, new Count(), null, null);
            metrics.put(metricName, metric);
            MetricName appInfoMetricName =
                    new MetricName("a1", KafkaMetrics.METRIC_GROUP_APP_INFO, "c0",
                            new LinkedHashMap<>());
            KafkaMetric appInfoMetric =
                    new KafkaMetric(this, appInfoMetricName, new Count(), null, null);
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
            KafkaMetric metric = new KafkaMetric(this, metricName, new Count(), null, null);
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
        KafkaMetric metric = new KafkaMetric(this, metricName, new Count(), null, null);
        metrics.put(metricName, metric);
        //When
        kafkaMetrics.checkAndBindMetrics(registry);
        //Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(1);
    }
}
