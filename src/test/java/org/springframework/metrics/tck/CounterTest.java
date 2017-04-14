package org.springframework.metrics.tck;

import org.junit.Test;
import org.springframework.metrics.Counter;

import static org.junit.Assert.assertEquals;

public abstract class CounterTest implements MetricsCompatibilityKit {
    @Test
    public void testIncrement() {
        Counter c = createRegistry().counter("myCounter");
        c.increment();
        assertEquals(c.count(), 1L);
        c.increment();
        c.increment();
        assertEquals(c.count(), 3L);
    }

    @Test
    public void testIncrementAmount() {
        Counter c = createRegistry().counter("myCounter");
        c.increment(2);
        assertEquals(c.count(), 2L);
    }
}
