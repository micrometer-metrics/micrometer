package io.micrometer.core.instrument.composite;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeCounterTest {
    @Test
    @Issue("#119")
    void increment() {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        CompositeMeterRegistry registry = new CompositeMeterRegistry();
        registry.add(simple);

        registry.counter("counter").increment(2.0);

        assertThat(simple.find("counter").value(Statistic.Count, 2.0).counter()).isPresent();
    }
}
