package io.micrometer.spring.samples.library;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meters;

import javax.inject.Inject;

public class ProposalLibrary {

    // Pro
    // Rock solid solution for dependency injection
    // Minimizes composite registry redirection
    // Allows registry substitution for testing

    // Con
    // Requires @Inject on library classpath
    // Complex to cover each step: Static registry ref, LazyCounter, @Inject, and have to register to DI system (expose need to user)


    @Inject MeterRegistry registry = MeterRegistry.globalRegistry;
    Counter workCounter = Meters.lazyCounter(() -> registry.counter("work_counter"));

    public void doWork() {
            workCounter.increment();
    }

}
