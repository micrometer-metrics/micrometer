package io.micrometer.spring.samples.library;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

public class NoOpRegistryLibrary {

    // Pro
    // Simple constructor usage
    // Minimizes redirection between composite registry
    // Allows registry substitution for testing

    // Con
    // Requires registry to be injected. (Exposes to user that metrics are collected, can be masked via noop registry)

    // Questions
    // Should CompositeMeter can be used as a NoOpRegistry?

    private Counter workCounter;

    public NoOpRegistryLibrary() {
        this(new CompositeMeterRegistry());
    }

    public NoOpRegistryLibrary(MeterRegistry registry) {
        this.workCounter = registry.counter("work_counter");
    }

    public void doWork() {
        workCounter.increment();
    }

}
