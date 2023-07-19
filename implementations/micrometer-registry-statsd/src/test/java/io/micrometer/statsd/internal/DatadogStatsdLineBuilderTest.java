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
package io.micrometer.statsd.internal;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatadogStatsdLineBuilderTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void changingNamingConvention() {
        Counter c = registry.counter("my.counter", "my.tag", "value");
        DatadogStatsdLineBuilder lb = new DatadogStatsdLineBuilder(c.getId(), registry.config());

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("my.counter:1|c|#statistic:count,my.tag:value");

        registry.config().namingConvention(NamingConvention.camelCase);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("myCounter:1|c|#statistic:count,myTag:value");
    }

    @Test
    void useDistributions() {
        DistributionSummary s = registry.summary("my.summary", "tag", "value");
        DatadogStatsdLineBuilder lb = new DatadogStatsdLineBuilder(s.getId(), registry.config(),
                DistributionStatisticConfig.builder().percentilesHistogram(true).build());

        assertThat(lb.histogram(1.0)).isEqualTo("my_summary:1|d|#tag:value");
    }

    @Test
    void useHistograms() {
        DistributionSummary s = registry.summary("my.summary", "tag", "value");
        DatadogStatsdLineBuilder lb = new DatadogStatsdLineBuilder(s.getId(), registry.config(),
                DistributionStatisticConfig.builder().percentilesHistogram(false).build());

        assertThat(lb.histogram(1.0)).isEqualTo("my_summary:1|h|#tag:value");
    }

    @Issue("#739")
    @Test
    void sanitizeColonsInTagKeys() {
        Counter c = registry.counter("my:counter", "my:tag", "my_value");
        DatadogStatsdLineBuilder lb = new DatadogStatsdLineBuilder(c.getId(), registry.config());

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("my_counter:1|c|#statistic:count,my_tag:my_value");
    }

    @Test
    void interpretEmptyTagValuesAsValuelessTags() {
        Counter c = registry.counter("my:counter", "my:tag", "");
        DatadogStatsdLineBuilder lb = new DatadogStatsdLineBuilder(c.getId(), registry.config());

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("my_counter:1|c|#statistic:count,my_tag");
    }

    @Issue("#2417")
    @Test
    void appendDdEntityIdTag() {
        Counter c = registry.counter("my:counter", "mytag", "myvalue");
        DatadogStatsdLineBuilder lb = new DatadogStatsdLineBuilder(c.getId(), registry.config());
        lb.ddEntityId = "test-entity-id";

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c"))
            .isEqualTo("my_counter:1|c|#statistic:count,mytag:myvalue,dd.internal.entity_id:test-entity-id");
    }

    @Issue("#1998")
    @Test
    void allowColonsInTagValues() {
        Counter c = registry.counter("my:counter", "my:tag", "my:value", "other_tag", "some:value:", "123.another.tag",
                "123:value");
        DatadogStatsdLineBuilder lb = new DatadogStatsdLineBuilder(c.getId(), registry.config());

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo(
                "my_counter:1|c|#statistic:count,m.123.another.tag:123:value,my_tag:my:value,other_tag:some:value_");
    }

}
