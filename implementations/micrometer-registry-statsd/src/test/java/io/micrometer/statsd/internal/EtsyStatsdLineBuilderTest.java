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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EtsyStatsdLineBuilderTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void changingNamingConvention() {
        Counter c = registry.counter("my.counter", "my.tag", "value");
        EtsyStatsdLineBuilder lb = new EtsyStatsdLineBuilder(c.getId(), registry.config(),
                HierarchicalNameMapper.DEFAULT);

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("my.counter.my.tag.value.statistic.count:1|c");

        registry.config().namingConvention(NamingConvention.camelCase);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("myCounter.myTag.value.statistic.count:1|c");
    }

    @Issue("#739")
    @Test
    void sanitizeColons() {
        Counter c = registry.counter("my:counter", "my:tag", "my:value");
        EtsyStatsdLineBuilder lb = new EtsyStatsdLineBuilder(c.getId(), registry.config(),
                HierarchicalNameMapper.DEFAULT);

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("my_counter.my_tag.my_value.statistic.count:1|c");
    }

}
