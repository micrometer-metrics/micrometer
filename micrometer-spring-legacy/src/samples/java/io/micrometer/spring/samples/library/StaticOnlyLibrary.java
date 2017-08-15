package io.micrometer.spring.samples.library;

import io.micrometer.core.instrument.MeterRegistry;

public class StaticOnlyLibrary {

    public void doWork() {
        MeterRegistry.globalRegistry.counter("my_counter").increment();
    }

}
