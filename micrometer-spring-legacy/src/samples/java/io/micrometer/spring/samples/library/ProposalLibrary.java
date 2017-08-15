package io.micrometer.spring.samples.library;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meters;

import javax.inject.Inject;

public class ProposalLibrary {

    @Inject MeterRegistry registry = MeterRegistry.globalRegistry;
    Counter workCounter = Meters.lazyCounter(() -> registry.counter("work_counter"));

    public void doWork() {
            workCounter.increment();
    }

}
