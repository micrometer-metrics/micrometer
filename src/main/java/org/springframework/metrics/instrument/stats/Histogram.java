package org.springframework.metrics.instrument.stats;

import java.util.Set;

/**
 * @author Jon Schneider
 */
public interface Histogram<T> {
    /**
     * Add a sample
     * @param value
     */
    void observe(double value);

    Double get(T bucket);

    Set<T> buckets();
}