package io.micrometer.appdynamics.aggregation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetricAggregatorTest {

    private final MetricAggregator victim = new MetricAggregator();

    @Test
    void testNegativeValuesAreIgnored() {
        victim.recordNonNegative(-100);
        assertRecordedValues(victim, 0, 0, 0, 0);
    }

    @Test
    void testRecordedValues() {
        victim.recordNonNegative(-100);
        victim.recordNonNegative(100);
        victim.recordNonNegative(50);
        assertRecordedValues(victim, 2, 50, 100, 150);

        victim.recordNonNegative(120);
        victim.recordNonNegative(-100);
        assertRecordedValues(victim, 3, 50, 120, 270);
    }

    @Test
    void testResetRecordedValues() {
        victim.recordNonNegative(-100);
        victim.recordNonNegative(100);
        victim.recordNonNegative(50);
        assertRecordedValues(victim, 2, 50, 100, 150);

        victim.reset();
        assertRecordedValues(victim, 0, 0, 0, 0);
    }

    private void assertRecordedValues(MetricAggregator aggregator, long count, long min, long max, long total) {
        assertEquals(count, aggregator.count());
        assertEquals(min, aggregator.min());
        assertEquals(max, aggregator.max());
        assertEquals(total, aggregator.total());
    }

}
