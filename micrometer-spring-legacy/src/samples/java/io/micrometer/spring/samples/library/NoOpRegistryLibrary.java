package io.micrometer.spring.samples.library;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

public class NoOpRegistryLibrary {

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
