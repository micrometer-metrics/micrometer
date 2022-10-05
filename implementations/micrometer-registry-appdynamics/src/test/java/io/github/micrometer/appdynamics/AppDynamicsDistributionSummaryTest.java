package io.github.micrometer.appdynamics;

import io.github.micrometer.appdynamics.aggregation.MetricSnapshot;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AppDynamicsDistributionSummaryTest {

    private AppDynamicsDistributionSummary victim;

    @BeforeEach
    void initialize() {
        Meter.Id id = new Meter.Id("test.id", Tags.empty(), null, null, Meter.Type.DISTRIBUTION_SUMMARY);

        victim = new AppDynamicsDistributionSummary(id, new MockClock(), 1);
    }

    @Test
    void testNegativeValuesAreIgnored() {
        victim.record(-100);
        assertRecordedValues(victim, 0, 0, 0, 0);
    }

    @Test
    void testRecordedValues() {
        victim.record(-100);
        victim.record(100);
        victim.record(115);
        assertRecordedValues(victim, 2, 100, 115, 215);

        victim.record(120);
        assertRecordedValues(victim.snapshot(), 3, 100, 120, 335);

        victim.record(-100);
        assertRecordedValues(victim, 0, 0, 0, 0);
        assertRecordedValues(victim.snapshot(), 0, 0, 0, 0);
    }

    @Test
    void testScaledRecordedValues() {
        final double scale = 1.5;

        Meter.Id id = new Meter.Id("test.id", Tags.empty(), null, null, Meter.Type.DISTRIBUTION_SUMMARY);
        AppDynamicsDistributionSummary victim = new AppDynamicsDistributionSummary(id, new MockClock(), scale);

        victim.record(-100);
        victim.record(100);
        victim.record(120);
        assertRecordedValues(victim, 2, (long) (100 * scale), (long) (120 * scale), (long) (220 * scale));
    }

    private void assertRecordedValues(AppDynamicsDistributionSummary summary, long count, long min, long max,
            long total) {
        assertEquals(count, summary.count());
        assertEquals(min, summary.min());
        assertEquals(max, summary.max());
        assertEquals(total, summary.totalAmount());
    }

    private void assertRecordedValues(MetricSnapshot snapshot, long count, long min, long max, long total) {
        assertEquals(count, snapshot.count());
        assertEquals(min, snapshot.min());
        assertEquals(max, snapshot.max());
        assertEquals(total, snapshot.total());
    }

}
