package org.springframework.metrics.instrument;

import java.util.function.Predicate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class Assertions {
    public static void assertGaugeValue(MeterRegistry registry, String name, Predicate<Double> valueTest) {
        assertThat(registry.findOne(name))
                .containsInstanceOf(Gauge.class)
                .map(g -> (Gauge) g)
                .hasValueSatisfying(g -> assertThat(g.value()).matches(valueTest));
    }
}
