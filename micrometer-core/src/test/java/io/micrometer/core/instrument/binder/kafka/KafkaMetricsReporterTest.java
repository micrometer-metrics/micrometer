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
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.metrics.stats.CumulativeSum;
import org.apache.kafka.common.metrics.stats.Value;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaMetricsReporterTest {

    private KafkaMetricsReporter reporter;

    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        KafkaMetricsReporter.setDefaultMeterRegistry(registry);
        reporter = new KafkaMetricsReporter();
        reporter.configure(Collections.emptyMap());
    }

    @AfterEach
    void tearDown() {
        if (reporter != null) {
            reporter.close();
        }
    }

    @Test
    void shouldRegisterMetricsOnInit() {
        // Given
        List<KafkaMetric> metrics = new ArrayList<>();
        metrics.add(createKafkaMetric("a", "b", "c", Collections.emptyMap(), 1.0));

        // When
        reporter.init(metrics);

        // Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getName()).isEqualTo("kafka.b.a");
    }

    @Test
    void shouldRegisterMetricOnMetricChange() {
        // Given
        reporter.init(Collections.emptyList());
        KafkaMetric metric = createKafkaMetric("a", "b", "c", Collections.emptyMap(), 1.0);

        // When
        reporter.metricChange(metric);

        // Then
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void shouldRemoveMetricOnMetricRemoval() {
        // Given
        KafkaMetric metric = createKafkaMetric("a", "b", "c", Collections.emptyMap(), 1.0);
        reporter.init(Collections.singletonList(metric));
        assertThat(registry.getMeters()).hasSize(1);

        // When
        reporter.metricRemoval(metric);

        // Then
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void shouldRemoveAllMetersOnClose() {
        // Given
        List<KafkaMetric> metrics = new ArrayList<>();
        metrics.add(createKafkaMetric("a", "b", "c", Collections.emptyMap(), 1.0));
        metrics.add(createKafkaMetric("d", "e", "f", Collections.emptyMap(), 2.0));
        reporter.init(metrics);
        assertThat(registry.getMeters()).hasSize(2);

        // When
        reporter.close();

        // Then
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void shouldNotAddAppInfoMetrics() {
        // Given
        List<KafkaMetric> metrics = new ArrayList<>();
        metrics.add(createKafkaMetric("a", "b", "c", Collections.emptyMap(), 1.0));
        metrics.add(createKafkaMetric("version", KafkaMetricsReporter.METRIC_GROUP_APP_INFO, "version",
                Collections.emptyMap(), 0.0));

        // When
        reporter.init(metrics);

        // Then
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void shouldExtractKafkaVersionFromAppInfo() {
        // Given
        List<KafkaMetric> metrics = new ArrayList<>();
        metrics.add(createKafkaMetric("a", "b", "c", Collections.emptyMap(), 1.0));

        // When
        reporter.init(metrics);

        // Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTag("kafka.version")).isEqualTo("unknown");
    }

    @Test
    void shouldRemoveOlderMeterWithLessTags() {
        // Given
        KafkaMetric metricWithoutTags = createKafkaMetric("a", "b", "c", Collections.emptyMap(), 1.0);
        reporter.init(Collections.singletonList(metricWithoutTags));
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(1);

        // When
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("key0", "value0");
        KafkaMetric metricWithTags = createKafkaMetric("a", "b", "c", tags, 2.0);
        reporter.metricChange(metricWithTags);

        // Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(2);
    }

    @Test
    void shouldRemoveMeterWithLessTags() {
        // Given
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("key0", "value0");
        KafkaMetric metricWithTags = createKafkaMetric("a", "b", "c", tags, 1.0);
        KafkaMetric metricWithoutTags = createKafkaMetric("a", "b", "c", Collections.emptyMap(), 2.0);

        List<KafkaMetric> metrics = new ArrayList<>();
        metrics.add(metricWithoutTags);
        metrics.add(metricWithTags);

        // When
        reporter.init(metrics);

        // Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTags()).hasSize(2);
    }

    @Test
    void shouldBindMetersWithSameTags() {
        // Given
        Map<String, String> firstTags = new LinkedHashMap<>();
        firstTags.put("key0", "value0");
        Map<String, String> secondTags = new LinkedHashMap<>();
        secondTags.put("key0", "value1");

        List<KafkaMetric> metrics = new ArrayList<>();
        metrics.add(createKafkaMetric("a", "b", "c", firstTags, 1.0));
        metrics.add(createKafkaMetric("a", "b", "c", secondTags, 2.0));

        // When
        reporter.init(metrics);

        // Then
        assertThat(registry.getMeters()).hasSize(2);
    }

    @Issue("#1968")
    @Test
    void shouldBindMetersWithDifferentClientIds() {
        // Given
        Map<String, String> firstTags = new LinkedHashMap<>();
        firstTags.put("key0", "value0");
        firstTags.put("client-id", "client0");
        Map<String, String> secondTags = new LinkedHashMap<>();
        secondTags.put("key0", "value0");
        secondTags.put("client-id", "client1");

        List<KafkaMetric> metrics = new ArrayList<>();
        metrics.add(createKafkaMetric("a", "b", "c", firstTags, 1.0));
        metrics.add(createKafkaMetric("a", "b", "c", secondTags, 2.0));

        // When
        reporter.init(metrics);

        // Then
        assertThat(registry.getMeters()).hasSize(2);
    }

    @Issue("#2212")
    @Test
    void shouldRemoveMeterWithLessTagsWithMultipleClients() {
        // Given
        Map<String, String> firstTags = new LinkedHashMap<>();
        firstTags.put("key0", "value0");
        firstTags.put("client-id", "client0");
        Map<String, String> secondTags = new LinkedHashMap<>();
        secondTags.put("key0", "value0");
        secondTags.put("client-id", "client1");

        List<KafkaMetric> metrics = new ArrayList<>();
        metrics.add(createKafkaMetric("a", "b", "c", firstTags, 1.0));
        metrics.add(createKafkaMetric("a", "b", "c", secondTags, 2.0));
        reporter.init(metrics);
        assertThat(registry.getMeters()).hasSize(2);

        // When
        Map<String, String> firstTagsWithMore = new LinkedHashMap<>();
        firstTagsWithMore.put("key0", "value0");
        firstTagsWithMore.put("client-id", "client0");
        firstTagsWithMore.put("key1", "value1");
        Map<String, String> secondTagsWithMore = new LinkedHashMap<>();
        secondTagsWithMore.put("key0", "value0");
        secondTagsWithMore.put("client-id", "client1");
        secondTagsWithMore.put("key1", "value1");

        reporter.metricChange(createKafkaMetric("a", "b", "c", firstTagsWithMore, 3.0));
        reporter.metricChange(createKafkaMetric("a", "b", "c", secondTagsWithMore, 4.0));

        // Then
        assertThat(registry.getMeters()).hasSize(2);
        registry.getMeters()
            .forEach(meter -> assertThat(meter.getId().getTags()).extracting(Tag::getKey)
                .containsOnly("key0", "key1", "client.id", "kafka.version"));
    }

    @Test
    void shouldUpdateMetricReferenceOnMetricChange() {
        // Given
        KafkaMetric oldMetric = createKafkaMetric("a", "b", "c", Collections.emptyMap(), 1.0);
        reporter.init(Collections.singletonList(oldMetric));

        Gauge gauge = registry.find("kafka.b.a").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(1.0);

        // When
        KafkaMetric newMetric = createKafkaMetric("a", "b", "c", Collections.emptyMap(), 99.0);
        reporter.metricChange(newMetric);

        // Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(gauge.value()).isEqualTo(99.0);
    }

    @Test
    void shouldNotAddKafkaMetricsCountGroup() {
        // Given
        List<KafkaMetric> metrics = new ArrayList<>();
        metrics.add(createKafkaMetric("a", "b", "c", Collections.emptyMap(), 1.0));
        metrics.add(createKafkaMetric("count", KafkaMetricsReporter.METRIC_GROUP_METRICS_COUNT, "count",
                Collections.emptyMap(), 10.0));

        // When
        reporter.init(metrics);

        // Then
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void shouldRegisterStartTimeMetricFromAppInfo() {
        // Given
        List<KafkaMetric> metrics = new ArrayList<>();
        metrics.add(createKafkaMetric(KafkaMetricsReporter.START_TIME_METRIC_NAME,
                KafkaMetricsReporter.METRIC_GROUP_APP_INFO, "start time", Collections.emptyMap(),
                (double) System.currentTimeMillis()));

        // When
        reporter.init(metrics);

        // Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getName()).contains("start.time.ms");
    }

    @Test
    void shouldUseGlobalRegistryWhenNoDefaultSet() {
        // Given
        SimpleMeterRegistry tempRegistry = new SimpleMeterRegistry();
        KafkaMetricsReporter.setDefaultMeterRegistry(tempRegistry);
        KafkaMetricsReporter newReporter = new KafkaMetricsReporter();

        // When
        newReporter.configure(Collections.emptyMap());
        newReporter.init(Collections.singletonList(createKafkaMetric("a", "b", "c", Collections.emptyMap(), 1.0)));

        // Then
        assertThat(tempRegistry.getMeters()).hasSize(1);

        newReporter.close();
    }

    @Test
    void shouldConvertHyphensToDotsInMeterName() {
        // Given
        KafkaMetric metric = createKafkaMetric("records-lag-max", "consumer-fetch-manager-metrics", "description",
                Collections.emptyMap(), 100.0);

        // When
        reporter.init(Collections.singletonList(metric));

        // Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getName())
            .isEqualTo("kafka.consumer.fetch.manager.records.lag.max");
    }

    @Test
    void shouldConvertHyphensToDotsInTagKeys() {
        // Given
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("client-id", "my-consumer");
        KafkaMetric metric = createKafkaMetric("a", "b", "c", tags, 1.0);

        // When
        reporter.init(Collections.singletonList(metric));

        // Then
        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.getMeters().get(0).getId().getTag("client.id")).isEqualTo("my-consumer");
    }

    @Test
    void shouldRegisterGaugeForMetricsEndingWithTotal() {
        // Given
        KafkaMetric metric = createKafkaMetric("bytes-consumed-total", "consumer-fetch-manager-metrics", "description",
                Collections.emptyMap(), 1000.0);

        // When
        reporter.init(Collections.singletonList(metric));

        // Then
        assertThat(registry.getMeters()).hasSize(1);
        Meter meter = registry.getMeters().get(0);
        assertThat(meter.getId().getName()).endsWith("total");
        assertThat(meter).isInstanceOf(Gauge.class);
    }

    @Test
    void shouldRegisterFunctionCounterForCumulativeSumMetrics() {
        // Given
        MetricName metricName = new MetricName("bytes-consumed-total", "consumer-fetch-manager-metrics", "description",
                Collections.emptyMap());
        CumulativeSum cumulativeSum = new CumulativeSum();
        cumulativeSum.record(new MetricConfig(), 1000.0, System.currentTimeMillis());
        KafkaMetric metric = new KafkaMetric(this, metricName, cumulativeSum, new MetricConfig(), Time.SYSTEM);

        // When
        reporter.init(Collections.singletonList(metric));

        // Then
        assertThat(registry.getMeters()).hasSize(1);
        Meter meter = registry.getMeters().get(0);
        assertThat(meter).isInstanceOf(FunctionCounter.class);
    }

    private KafkaMetric createKafkaMetric(String name, String group, String description, Map<String, String> tags,
            double value) {
        MetricName metricName = new MetricName(name, group, description, tags);
        Value kafkaValue = new Value();
        kafkaValue.record(new MetricConfig(), value, System.currentTimeMillis());
        return new KafkaMetric(this, metricName, kafkaValue, new MetricConfig(), Time.SYSTEM);
    }

}
