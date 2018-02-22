package io.micrometer.core.samples;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.samples.utils.SampleConfig;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates how monitoring systems deal with NaN values coming out of gauges.
 */
public class NullGaugeSample {
    public static void main(String[] args) throws InterruptedException {
        MeterRegistry registry = SampleConfig.myMonitoringSystem();
        AtomicInteger n = new AtomicInteger(100);

        registry.gauge("my.null.gauge", (Object) null, o -> 1.0);
        registry.gauge("my.nonnull.gauge", n);

        for(;;) {
            Thread.sleep(100);
        }
    }
}
