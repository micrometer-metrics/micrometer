package io.micrometer.core.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.samples.utils.Registries;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class CounterSample {
    public static void main(String[] args) {
        Counter counter = Registries.prometheus().counter("counter");

        RandomEngine r = new MersenneTwister64(0);
        Normal dist = new Normal(0, 1, r);

        Flux.interval(Duration.ofMillis(10))
                .doOnEach(d -> {
                    if (dist.nextDouble() + 0.1 > 0) {
                        counter.increment();
                    }
                })
                .blockLast();
    }
}