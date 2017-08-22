package io.micrometer.spring.samples.library;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class StaticOnlyLibrary {

    //Pro
    // Simple and straight-forward
    //

    //Con
    // Unable to swap out registry for testing purposes
    // Counter is always of type 'CompositeCounter' so requires redirection to increment underlying counter

    private final Counter counter = MeterRegistry.globalRegistry.counter("my_counter");

    public void doWork() {
        counter.increment();
    }

}
