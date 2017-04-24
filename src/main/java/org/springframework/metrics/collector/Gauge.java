package org.springframework.metrics.collector;

public interface Gauge extends Meter {
    /**
     * Returns the current value. The act of observing the value by calling this method triggers sampling
     * of the underlying number or user-defined function that defines the value for the gauge.
     */
    double value();
}
