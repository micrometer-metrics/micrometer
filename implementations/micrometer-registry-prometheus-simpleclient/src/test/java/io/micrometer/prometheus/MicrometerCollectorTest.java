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
package io.micrometer.prometheus;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.NamingConvention;
import io.prometheus.client.Collector;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class MicrometerCollectorTest {

    NamingConvention convention = NamingConvention.snakeCase;

    @Issue("#769")
    @Test
    void manyTags() {
        Meter.Id id = Metrics.counter("my.counter").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id, convention,
                PrometheusConfig.DEFAULT);

        for (Integer i = 0; i < 20_000; i++) {
            Collector.MetricFamilySamples.Sample sample = new Collector.MetricFamilySamples.Sample("my_counter",
                    singletonList("k"), singletonList(i.toString()), 1.0);

            collector.add(Collections.emptyList(), (conventionName, tagKeys) -> Stream
                .of(new MicrometerCollector.Family(Collector.Type.COUNTER, "my_counter", sample)));
        }

        // Threw StackOverflowException because of too many nested streams originally
        collector.collect();
    }

    @Test
    void sameValuesDifferentOrder() {
        Meter.Id id = Metrics.counter("my.counter").getId();
        MicrometerCollector collector = new MicrometerCollector(id.getConventionName(convention), id, convention,
                PrometheusConfig.DEFAULT);

        Collector.MetricFamilySamples.Sample sample = new Collector.MetricFamilySamples.Sample("my_counter",
                asList("k", "k2"), asList("v1", "v2"), 1.0);
        Collector.MetricFamilySamples.Sample sample2 = new Collector.MetricFamilySamples.Sample("my_counter",
                asList("k", "k2"), asList("v2", "v1"), 1.0);

        collector.add(asList("v1", "v2"), (conventionName, tagKeys) -> Stream
            .of(new MicrometerCollector.Family(Collector.Type.COUNTER, "my_counter", sample)));
        collector.add(asList("v2", "v1"), (conventionName, tagKeys) -> Stream
            .of(new MicrometerCollector.Family(Collector.Type.COUNTER, "my_counter", sample2)));

        assertThat(collector.collect().get(0).samples).hasSize(2);
    }

}
