package org.springframework.metrics.tck;

import org.junit.Test;
import org.springframework.metrics.Timer;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public abstract class TimerTest implements MetricsCompatibilityKit {

    @Test
    public void testRecord() {
        Timer t = createRegistry().timer("myTimer");
        t.record(42, TimeUnit.MILLISECONDS);
        assertEquals(t.count(), 1L);
        assertEquals(t.totalTime(), 42000000L);
    }

    @Test
    public void testRecordNegative() {
        Timer t = createRegistry().timer("myTimer");
        t.record(-42, TimeUnit.MILLISECONDS);
        assertEquals(t.count(), 0L);
        assertEquals(t.totalTime(), 0L);
    }

    @Test
    public void testRecordZero() {
        Timer t = createRegistry().timer("myTimer");
        t.record(0, TimeUnit.MILLISECONDS);
        assertEquals(t.count(), 1L);
        assertEquals(t.totalTime(), 0L);
    }
}
