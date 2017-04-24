package org.springframework.metrics.collector;

public interface Clock {
    long monotonicTime();
}
