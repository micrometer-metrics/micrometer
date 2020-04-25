/**
 * Copyright 2020.
 */

package io.micrometer.core.instrument.distribution;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import io.micrometer.core.instrument.Clock;

/**
 * Tests TimeWindowStats
 */
public class TimeWindowStatsTest {

    Clock clock = Mockito.mock(Clock.class, (Answer<Long>) e -> 1L);
    final TimeWindowStats tws = new TimeWindowStats(clock, 5, 2);

    /** Tests that TimeWindowStats inits correctly */
    @Test
    void testInit() {
        Assert.assertEquals(Double.MAX_VALUE, tws.min(), 0D);
        Assert.assertEquals(0D, tws.max(), 0D);
        Assert.assertTrue(Double.isNaN(tws.mean()));
        Assert.assertEquals(0, tws.sum(), 0D);
        Assert.assertEquals(0, tws.count(), 0D);
        Assert.assertTrue(Double.isNaN(tws.freq()));
        Assert.assertEquals(0, tws.age(), 0D);

        Mockito.when(clock.wallTime()).thenReturn(2L);
        Assert.assertEquals(1, tws.age(), 0D);
        Assert.assertEquals(0, tws.freq(), 0D);
    }

    /** Tests that TimeWindowStats behaves correctly */
    @Test
    void testBehavior() {
        Mockito.when(clock.wallTime()).thenReturn(2L);
        tws.record(50D);
        Assert.assertEquals(50D, tws.min(), 0.00000001D);
        Assert.assertEquals(50D, tws.max(), 0.00000001D);
        Assert.assertEquals(50D, tws.sum(), 0.00000001D);
        Assert.assertEquals(50D, tws.mean(), 0.00000001D);
        Assert.assertEquals(1D, tws.count(), 0.00000001D);

        tws.record(60D);
        Assert.assertEquals(50D, tws.min(), 0.00000001D);
        Assert.assertEquals(60D, tws.max(), 0.00000001D);
        Assert.assertEquals(110D, tws.sum(), 0.00000001D);
        Assert.assertEquals(55D, tws.mean(), 0.00000001D);
        Assert.assertEquals(2D, tws.count(), 0.00000001D);

        tws.record(40D);
        Assert.assertEquals(40D, tws.min(), 0.00000001D);
        Assert.assertEquals(60D, tws.max(), 0.00000001D);
        Assert.assertEquals(150D, tws.sum(), 0.00000001D);
        Assert.assertEquals(50D, tws.mean(), 0.00000001D);
        Assert.assertEquals(3D, tws.count(), 0.00000001D);

        tws.record(1D);
        Assert.assertEquals(1D, tws.min(), 0.00000001D);
        Assert.assertEquals(60D, tws.max(), 0.00000001D);
        Assert.assertEquals(151D, tws.sum(), 0.00000001D);
        Assert.assertEquals(4D, tws.count(), 0.00000001D);

        tws.record(1000D);
        Assert.assertEquals(1D, tws.min(), 0.00000001D);
        Assert.assertEquals(1000D, tws.max(), 0.00000001D);
        Assert.assertEquals(1151D, tws.sum(), 0.00000001D);
        Assert.assertEquals(5D, tws.count(), 0.00000001D);

        tws.record(0.0000001D);
        Assert.assertEquals(0.0000001D, tws.min(), 0.0000000001D);
        Assert.assertEquals(1000D, tws.max(), 0.00000001D);
        Assert.assertEquals(1151.0000001D, tws.sum(), 0.0000000001D);
        Assert.assertEquals(6D, tws.count(), 0.00000001D);

        tws.record(0D);
        Assert.assertEquals(0D, tws.min(), 0.000000000000001D);
        Assert.assertEquals(1151.0000001D, tws.sum(), 0.0000000001D);
        Assert.assertEquals(7D, tws.count(), 0.00000001D);

        //Ignored samples
        tws.record(-1D);
        Assert.assertEquals(7D, tws.count(), 0.00000001D);

        //Frequency
        Assert.assertEquals(7D, tws.freq(), 0.00000001D);
        Mockito.when(clock.wallTime()).thenReturn(3L);
        Assert.assertEquals(3.5D, tws.freq(), 0.00000001D);

        //Age
        Mockito.when(clock.wallTime()).thenReturn(4L);
        Mockito.when(clock.wallTime()).thenReturn(4L);

        //First rotation
        Mockito.when(clock.wallTime()).thenReturn(7L);
        tws.record(80D);
        Assert.assertEquals(8D, tws.count(), 0.00000001D);

        Mockito.when(clock.wallTime()).thenReturn(12L);
        Assert.assertEquals(1D, tws.count(), 0.00000001D);
        
        Assert.assertEquals(tws.sum(), tws.getSnapshot().sum(), 0D);
        Assert.assertEquals(tws.min(), tws.getSnapshot().min(), 0D);
        Assert.assertEquals(tws.max(), tws.getSnapshot().max(), 0D);
        Assert.assertEquals(tws.count(), tws.getSnapshot().count(), 0D);
        Assert.assertEquals(tws.mean(), tws.getSnapshot().mean(), 0D);
        Assert.assertEquals(tws.freq(), tws.getSnapshot().freq(), 0D);
        Assert.assertEquals(tws.age(), tws.getSnapshot().age(), 0D);
    }
}
