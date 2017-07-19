package io.micrometer.core.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.samples.utils.Registries;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TimerSample {
    public static void main(String[] args) {
        Timer timer = Registries.prometheus().timer("timer", "instance", "local");
        Timer timer2 = Registries.prometheus().timer("timer", "instance", "cloud");

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
                        timer2.record(latencyForThisSecond.get(), TimeUnit.MILLISECONDS);
                    }
                })
                .blockLast();
    }
}
