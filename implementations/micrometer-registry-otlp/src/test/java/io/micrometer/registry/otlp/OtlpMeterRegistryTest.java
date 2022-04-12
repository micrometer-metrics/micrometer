package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class OtlpMeterRegistryTest {

    OtlpMeterRegistry registry = new OtlpMeterRegistry(OtlpConfig.DEFAULT, Clock.SYSTEM);

    @Test
    void sendSomeData() {
        Counter counter = registry.counter("log.event");
        counter.increment();
        counter.increment();
        Timer timer = registry.timer("http.client.requests");
        timer.record(10, TimeUnit.MILLISECONDS);
        timer.record(77, TimeUnit.MILLISECONDS);
        timer.record(111, TimeUnit.MILLISECONDS);
        registry.publish();
    }
}
