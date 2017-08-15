package io.micrometer.spring.samples.library;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meters;

public class ProposalLibrary {

    //@Inject no available on classpath
    MeterRegistry registry = MeterRegistry.globalRegistry;
    Counter workCounter = Meters.lazyCounter(() -> registry.counter("work_counter"));

    public void doWork() {
            workCounter.increment();
    }

}
