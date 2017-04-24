package org.springframework.metrics.collector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterTest {

    @DisplayName("multiple single increments are recorded")
    @ParameterizedTest
    @ArgumentsSource(MetricCollectorsProvider.class)
    void testIncrement(MetricCollector collector) {
        Counter c = collector.counter("myCounter");
        c.increment();
        assertEquals(c.count(), 1L);
        c.increment();
        c.increment();
        assertEquals(c.count(), 3L);
    }

    @DisplayName("increment by a non-negative amount")
    @ParameterizedTest
    @ArgumentsSource(MetricCollectorsProvider.class)
    void testIncrementAmount(MetricCollector collector) {
        Counter c = collector.counter("myCounter");
        c.increment(2);
        assertEquals(c.count(), 2L);
        c.increment(0);
        assertEquals(c.count(), 2L);
    }

    @DisplayName("increment by a negative amount")
    @ParameterizedTest
    @ArgumentsSource(MetricCollectorsProvider.class)
    void testIncrementAmountNegative(MetricCollector collector) {
        Counter c = collector.counter("myCounter");
        c.increment(-2);
        assertEquals(c.count(), -2L);
    }
}
