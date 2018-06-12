package io.micrometer.statsd.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatadogStatsdLineBuilderTest {
    @Test
    void changingNamingConvention() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Counter c = registry.counter("my.counter", "my.tag", "value");
        DatadogStatsdLineBuilder lb = new DatadogStatsdLineBuilder(c.getId(), registry.config());

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("my.counter:1|c|#statistic:count,my.tag:value");

        registry.config().namingConvention(NamingConvention.camelCase);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("myCounter:1|c|#statistic:count,myTag:value");
    }
}