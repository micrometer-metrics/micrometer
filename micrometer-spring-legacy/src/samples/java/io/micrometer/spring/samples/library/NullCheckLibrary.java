package io.micrometer.spring.samples.library;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meters;

public class NullCheckLibrary {

    // Pro
    // Simple constructor usage
    // Minimizes redirection between composite registry
    // Allows registry substitution for testing

    // Con
    // Adds lots of null checks in the library, very ugly


    private Counter workCounter;

    public NullCheckLibrary() {
        this(null);
    }

    public NullCheckLibrary(MeterRegistry registry) {
        if(registry != null) {
            this.workCounter = registry.counter("work_counter");
        }
    }


    public void doWork() {
        if(workCounter != null) {
            workCounter.increment();
        }
    }

}
