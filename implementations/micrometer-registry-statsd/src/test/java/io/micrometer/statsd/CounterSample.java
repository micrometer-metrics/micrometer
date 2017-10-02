package io.micrometer.statsd;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class CounterSample {
    public static void main(String[] args) {
        StatsdMeterRegistry registry = new StatsdMeterRegistry(k -> null, Clock.SYSTEM);

        Counter counter = registry.counter("my.counter", "k", "v");

        Flux.interval(Duration.ofSeconds(1))
            .doOnEach(s -> counter.increment())
            .blockLast();
    }
}
