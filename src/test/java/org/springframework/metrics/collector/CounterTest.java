package org.springframework.metrics.collector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterTest {

    @DisplayName("multiple increments are maintained")
    @ParameterizedTest
    @ArgumentsSource(MetricCollectorsProvider.class)
    void testIncrement(MetricCollector collector) {
        Counter c = collector.counter("myCounter");
        c.increment();
        assertEquals(1L, c.count());
        c.increment();
        c.increment();
        assertEquals(3L, c.count());
    }

    @DisplayName("increment by a non-negative amount")
    @ParameterizedTest
    @ArgumentsSource(MetricCollectorsProvider.class)
    void testIncrementAmount(MetricCollector collector) {
        Counter c = collector.counter("myCounter");
        c.increment(2);
        assertEquals(2L, c.count());
        c.increment(0);
        assertEquals(2L, c.count());
    }

    @DisplayName("increment by a negative amount")
    @ParameterizedTest
    @ArgumentsSource(MetricCollectorsProvider.class)
    void testIncrementAmountNegative(MetricCollector collector) {
        Counter c = collector.counter("myCounter");
        c.increment(-2);
        assertEquals(-2L, c.count());
    }
}
