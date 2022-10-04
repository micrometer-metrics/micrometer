package io.github.micrometer.appdynamics;

import io.github.micrometer.appdynamics.aggregation.MetricSnapshot;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.distribution.pause.NoPauseDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AppDynamicsTimerTest {

    private AppDynamicsTimer victim;

    @BeforeEach
    void initialize() {
        Meter.Id id = new Meter.Id("test.id", Tags.empty(), null, null, Meter.Type.TIMER);

        victim = new AppDynamicsTimer(id, new MockClock(), new NoPauseDetector(), TimeUnit.MILLISECONDS, 0);
    }

    @Test
    void testNegativeValuesAreIgnored() {
        victim.record(-100, TimeUnit.MILLISECONDS);
        assertRecordedValues(victim, 0, 0, 0, 0);
    }

    @Test
    void testRecordedValues() {
        victim.record(-100, TimeUnit.MILLISECONDS);
        victim.record(100, TimeUnit.MILLISECONDS);
        victim.record(1, TimeUnit.SECONDS);
        assertRecordedValues(victim, 2, 100, 1000, 1100);

        victim.record(120, TimeUnit.MILLISECONDS);
        assertRecordedValues(victim.snapshot(), 3, 100, 1000, 1220);

        victim.record(-100, TimeUnit.MILLISECONDS);
        assertRecordedValues(victim, 0, 0, 0, 0);
        assertRecordedValues(victim.snapshot(), 0, 0, 0, 0);
    }

    private void assertRecordedValues(AppDynamicsTimer timer, long count, long min, long max, long total) {
        assertEquals(count, timer.count());
        assertEquals(min, timer.min(timer.baseTimeUnit()));
        assertEquals(max, timer.max(timer.baseTimeUnit()));
        assertEquals(total, timer.totalTime(timer.baseTimeUnit()));
    }

    private void assertRecordedValues(MetricSnapshot snapshot, long count, long min, long max, long total) {
        assertEquals(count, snapshot.count());
        assertEquals(min, snapshot.min());
        assertEquals(max, snapshot.max());
        assertEquals(total, snapshot.total());
    }
}
