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
import io.micrometer.core.instrument.config.NamingConvention;
import io.prometheus.metrics.model.snapshots.CounterSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricMetadata;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class MicrometerCollectorTest {

    NamingConvention convention = NamingConvention.snakeCase;

    @Issue("#769")
    @Test
    void manyTags() {
        Meter.Id id = Metrics.counter("my.counter").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id, convention);

        for (int i = 0; i < 20_000; i++) {
            CounterSnapshot.CounterDataPointSnapshot sample = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                    Labels.of("k", Integer.toString(i)), null, 0);

            collector.add(Collections.emptyList(),
                    (conventionName,
                            tagKeys) -> Stream.of(new MicrometerCollector.Family<>(conventionName,
                                    family -> new CounterSnapshot(family.metadata, family.dataPointSnapshots),
                                    new MetricMetadata(conventionName), sample)));
        }

        // Threw StackOverflowException because of too many nested streams originally
        collector.collect();
    }

    @Test
    void sameValuesDifferentOrder() {
        Meter.Id id = Metrics.counter("my.counter").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id, convention);

        CounterSnapshot.CounterDataPointSnapshot sample = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                Labels.of("k", "v1", "k2", "v2"), null, 0);
        CounterSnapshot.CounterDataPointSnapshot sample2 = new CounterSnapshot.CounterDataPointSnapshot(1.0,
                Labels.of("k", "v2", "k2", "v1"), null, 0);

        collector.add(asList("v1", "v2"),
                (conventionName,
                        tagKeys) -> Stream.of(new MicrometerCollector.Family<>(conventionName,
                                family -> new CounterSnapshot(family.metadata, family.dataPointSnapshots),
                                new MetricMetadata(conventionName), sample)));
        collector.add(asList("v2", "v1"),
                (conventionName,
                        tagKeys) -> Stream.of(new MicrometerCollector.Family<>(conventionName,
                                family -> new CounterSnapshot(family.metadata, family.dataPointSnapshots),
                                new MetricMetadata(conventionName), sample2)));

        assertThat(collector.collect().get(0).getDataPoints()).hasSize(2);
    }

}
