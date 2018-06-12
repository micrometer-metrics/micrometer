package io.micrometer.statsd.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EtsyStatsdLineBuilderTest {
    @Test
    void changingNamingConvention() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Counter c = registry.counter("my.counter", "my.tag", "value");
        EtsyStatsdLineBuilder lb = new EtsyStatsdLineBuilder(c.getId(), registry.config(), HierarchicalNameMapper.DEFAULT);

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("my.counter.my.tag.value.statistic.count:1|c");

        registry.config().namingConvention(NamingConvention.camelCase);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("myCounter.myTag.value.statistic.count:1|c");
    }
}