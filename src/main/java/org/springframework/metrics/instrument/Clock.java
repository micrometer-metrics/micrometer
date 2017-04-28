package org.springframework.metrics.instrument;

public interface Clock {
    long monotonicTime();

    Clock SYSTEM = System::nanoTime;
}
