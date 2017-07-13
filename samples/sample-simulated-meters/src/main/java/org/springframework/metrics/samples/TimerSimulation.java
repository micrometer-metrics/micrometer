package org.springframework.metrics.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.samples.utils.Registries;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TimerSimulation {
    public static void main(String[] args) {
        Timer timer = Registries.atlas().timer("timer{");

        RandomEngine r = new MersenneTwister64(0);
        Normal incomingRequests = new Normal(0, 1, r);
        Normal duration = new Normal(250, 50, r);

        AtomicInteger latencyForThisSecond = new AtomicInteger(duration.nextInt());
        Flux.interval(Duration.ofSeconds(1))
                .doOnEach(d -> latencyForThisSecond.set(duration.nextInt()))
                .subscribe();

        // the potential for an "incoming request" every 10 ms
        Flux.interval(Duration.ofMillis(10))
                .doOnEach(d -> {
                    if (incomingRequests.nextDouble() + 0.4 > 0) {
                        // pretend the request took some amount of time, such that the time is
                        // distributed normally with a mean of 250ms
                        timer.record(latencyForThisSecond.get(), TimeUnit.MILLISECONDS);
                    }
                })
                .blockLast();
    }
}
