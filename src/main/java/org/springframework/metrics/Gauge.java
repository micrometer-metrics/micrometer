package org.springframework.metrics;

public interface Gauge extends Meter {
    /**
     * Set the current value of the gauge.
     *
     * @param value
     *     Most recent measured value.
     */
    void set(double value);

    /** Returns the current value. */
    double value();
}
