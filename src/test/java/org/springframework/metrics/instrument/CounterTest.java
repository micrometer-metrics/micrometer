package org.springframework.metrics.instrument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterTest {

    @DisplayName("multiple increments are maintained")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void increment(MeterRegistry collector) {
        Counter c = collector.counter("myCounter");
        c.increment();
        assertEquals(1L, c.count());
        c.increment();
        c.increment();
        assertEquals(3L, c.count());
    }

    @DisplayName("increment by a non-negative amount")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void incrementAmount(MeterRegistry collector) {
        Counter c = collector.counter("myCounter");
        c.increment(2);
        assertEquals(2L, c.count());
        c.increment(0);
        assertEquals(2L, c.count());
    }

    @DisplayName("increment by a negative amount")
    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    void incrementAmountNegative(MeterRegistry collector) {
        Counter c = collector.counter("myCounter");
        c.increment(-2);
        assertEquals(-2L, c.count());
    }
}
