package io.micrometer.statsd.internal;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.statsd.StatsdFlavor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlavorStatsdLineBuilderTest {
    @Issue("#644")
    @Test
    void escapeCharactersForTelegraf() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Counter c = registry.counter("hikari.pools", "pool", "poolname = abc,::hikari");

        FlavorStatsdLineBuilder lineBuilder = new FlavorStatsdLineBuilder(c.getId(), StatsdFlavor.TELEGRAF,
                HierarchicalNameMapper.DEFAULT, registry.config());

        assertThat(lineBuilder.count(1, Statistic.COUNT)).isEqualTo("hikari_pools,statistic=count,pool=poolname\\ \\=\\ abc\\,::hikari:1|c");
    }
}