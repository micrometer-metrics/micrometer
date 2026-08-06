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
package io.micrometer.prometheusmetrics;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.NamingConvention;
import io.prometheus.metrics.model.registry.MetricType;
import io.prometheus.metrics.model.snapshots.ClassicHistogramBuckets;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.HistogramSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MicrometerCollectorTest {

    NamingConvention convention = NamingConvention.snakeCase;

    @Issue("#769")
    @Test
    void manyTags() {
        Meter.Id id = Metrics.counter("my.counter").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id);
        MetricFamilyDescriptor descriptor = MetricFamilyDescriptor.counter(collector.conventionName).build();

        for (int i = 0; i < 20_000; i++) {
            id = id.withTag(Tag.of("k", Integer.toString(i)));
            CounterSnapshot.CounterDataPointSnapshot sample = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                    Labels.of("k", Integer.toString(i)), null, 0);

            collector.add(id, samples -> samples.accept(descriptor, sample));
        }

        // Threw StackOverflowException because of too many nested streams originally
        collector.collect();
    }

    @Test
    void sameValuesDifferentOrder() {
        Meter.Id id = Metrics.counter("my.counter").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id);
        MetricFamilyDescriptor descriptor = MetricFamilyDescriptor.counter(collector.conventionName).build();

        CounterSnapshot.CounterDataPointSnapshot sample = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                Labels.of("k", "v1", "k2", "v2"), null, 0);
        Meter.Id sampleId = id.withTags(Tags.of("k", "v1", "k2", "v2"));
        CounterSnapshot.CounterDataPointSnapshot sample2 = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                Labels.of("k", "v2", "k2", "v1"), null, 0);
        Meter.Id sample2Id = id.withTags(Tags.of("k", "v2", "k2", "v1"));

        collector.add(sampleId, samples -> samples.accept(descriptor, sample));
        collector.add(sample2Id, samples -> samples.accept(descriptor, sample2));

        assertThat(collector.collect().get(0).getDataPoints()).hasSize(2);
    }

    @Issue("#877")
    @Test
    void sameMetricDifferentTagKeysCounter() {
        Meter.Id id = Metrics.counter("my.counter", "k1", "v1", "k2", "v2", "k3", "v3").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id);
        MetricFamilyDescriptor descriptor = MetricFamilyDescriptor.counter(collector.conventionName).build();

        CounterSnapshot.CounterDataPointSnapshot sample = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                Labels.of(asList("k1", "k2", "k3"), asList("v1", "v2", "v3")), null, 0);
        CounterSnapshot.CounterDataPointSnapshot sample2 = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                Labels.of(asList("k1", "k4"), asList("v1", "v4")), null, 0);
        Meter.Id id2 = id.replaceTags(Tags.of("k1", "v1", "k4", "v4"));

        collector.add(id, samples -> samples.accept(descriptor, sample));
        collector.add(id2, samples -> samples.accept(descriptor, sample2));

        assertThat(collector.collect().get(0).getDataPoints()).hasSize(2);
    }

    @Issue("#877")
    @Test
    void oneSampleHasSubsetOfTagKeysOfAnotherSample() {
        Meter.Id id = Metrics.counter("my.counter", "k1", "v1", "k2", "v2", "k3", "v3").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id);
        MetricFamilyDescriptor descriptor = MetricFamilyDescriptor.counter(collector.conventionName).build();

        CounterSnapshot.CounterDataPointSnapshot sample = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                Labels.of(asList("k1", "k2", "k3"), asList("v1", "v2", "v3")), null, 0);
        CounterSnapshot.CounterDataPointSnapshot sample2 = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                Labels.of(asList("k1", "k2"), asList("v1", "v2")), null, 0);
        Meter.Id id2 = id.replaceTags(Tags.of("k1", "v1", "k2", "v2"));

        collector.add(id, samples -> samples.accept(descriptor, sample));
        collector.add(id2, samples -> samples.accept(descriptor, sample2));

        assertThat(collector.collect().get(0).getDataPoints()).hasSize(2);
    }

    @Issue("#877")
    @Test
    void sameMetricNameWithNoTagsAndAListOfTags() {
        Meter.Id id = Metrics.counter("my.counter", "k1", "v1", "k2", "v2", "k3", "v3").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id);
        MetricFamilyDescriptor descriptor = MetricFamilyDescriptor.counter(collector.conventionName).build();

        CounterSnapshot.CounterDataPointSnapshot sample = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                Labels.of(asList("k1", "k2", "k3"), asList("v1", "v2", "v3")), null, 0);
        CounterSnapshot.CounterDataPointSnapshot sample2 = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                Labels.EMPTY, null, 0);
        Meter.Id id2 = id.replaceTags(Tags.empty());

        collector.add(id, samples -> samples.accept(descriptor, sample));
        collector.add(id2, samples -> samples.accept(descriptor, sample2));

        assertThat(collector.collect().get(0).getDataPoints()).hasSize(2);
    }

    @Test
    void descriptorIsCreatedOnceAndReusedForTheSameFamily() {
        Meter.Id id = Metrics.counter("my.counter", "k", "v1").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id);

        MetricFamilyDescriptor descriptor = collector.getOrCreateDescriptor(MetricType.COUNTER,
                collector.conventionName, Collections.singletonList("k"), " ");
        MetricFamilyDescriptor descriptor2 = collector.getOrCreateDescriptor(MetricType.COUNTER,
                collector.conventionName, Collections.singletonList("k"), " ");

        assertThat(descriptor2).isSameAs(descriptor);
        assertThat(collector.getMetricFamilyDescriptors()).containsExactly(descriptor);
    }

    @Test
    void conflictingFamilyTypesUnderSameNameFail() {
        Meter.Id id = Metrics.timer("my.timer", "k", "v1").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id);

        MetricFamilyDescriptor summaryDescriptor = collector.getOrCreateDescriptor(MetricType.SUMMARY,
                collector.conventionName, Collections.singletonList("k"), " ");

        assertThatThrownBy(() -> collector.getOrCreateDescriptor(MetricType.HISTOGRAM, collector.conventionName,
                Collections.singletonList("k"), " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same Prometheus metric family types");

        // the conflicting type was not cached
        assertThat(collector.getMetricFamilyDescriptors()).containsExactly(summaryDescriptor);
    }

    @Test
    void longTaskTimerHistogramFamilyIsGaugeHistogram() {
        Meter.Id id = Metrics.more().longTaskTimer("my.ltt").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id);
        MetricFamilyDescriptor descriptor = MetricFamilyDescriptor.histogram(collector.conventionName).build();

        HistogramSnapshot.HistogramDataPointSnapshot dataPoint = new HistogramSnapshot.HistogramDataPointSnapshot(
                ClassicHistogramBuckets.of(asList(Double.POSITIVE_INFINITY), asList(0L)), 0.0, Labels.EMPTY, null, 0);
        collector.add(id, samples -> samples.accept(descriptor, dataPoint));

        MetricSnapshot snapshot = collector.collect().get(0);
        assertThat(snapshot).isInstanceOf(HistogramSnapshot.class);
        assertThat(((HistogramSnapshot) snapshot).isGaugeHistogram()).isTrue();
    }

    @Test
    void timerHistogramFamilyIsNotGaugeHistogram() {
        Meter.Id id = Metrics.timer("my.histogram.timer").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id);
        MetricFamilyDescriptor descriptor = MetricFamilyDescriptor.histogram(collector.conventionName).build();

        HistogramSnapshot.HistogramDataPointSnapshot dataPoint = new HistogramSnapshot.HistogramDataPointSnapshot(
                ClassicHistogramBuckets.of(asList(Double.POSITIVE_INFINITY), asList(0L)), 0.0, Labels.EMPTY, null, 0);
        collector.add(id, samples -> samples.accept(descriptor, dataPoint));

        MetricSnapshot snapshot = collector.collect().get(0);
        assertThat(snapshot).isInstanceOf(HistogramSnapshot.class);
        assertThat(((HistogramSnapshot) snapshot).isGaugeHistogram()).isFalse();
    }

    @Test
    void registrationDescriptorUsesPrometheusFamilyName() {
        Meter.Id id = Metrics.counter("my.counter").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id);
        collector.getOrCreateDescriptor(MetricType.COUNTER, "my_counter", List.of("k"), "help");

        assertThat(collector.getMetricFamilyDescriptors()).singleElement().satisfies(d -> {
            assertThat(d.getPrometheusName()).isEqualTo("my_counter");
            assertThat(d.getType()).isEqualTo(MetricType.COUNTER);
            assertThat(d.getLabelNames()).containsExactly("k");
            assertThat(d.getMetadata().getName()).isEqualTo("my_counter");
        });
    }

}
