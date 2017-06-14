package org.springframework.metrics.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import org.springframework.metrics.instrument.LongTaskTimer;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.samples.utils.Registries;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LongTaskTimerSimulation {
    public static void main(String[] args) {
        LongTaskTimer timer = Registries.prometheus().longTaskTimer("longTaskTimer");

        RandomEngine r = new MersenneTwister64(0);
        Normal incomingRequests = new Normal(0, 1, r);
        Normal duration = new Normal(30, 50, r);

        AtomicInteger latencyForThisSecond = new AtomicInteger(duration.nextInt());
        Flux.interval(Duration.ofSeconds(1))
                .doOnEach(d -> latencyForThisSecond.set(duration.nextInt()))
                .subscribe();

        final Map<Long, CountDownLatch> tasks = new ConcurrentHashMap<>();

        // the potential for an "incoming request" every 10 ms
        Flux.interval(Duration.ofSeconds(1))
                .doOnEach(d -> {
                    if (incomingRequests.nextDouble() + 0.4 > 0 && tasks.isEmpty()) {
                        int taskDur;
                        while((taskDur = duration.nextInt()) < 0);
                        synchronized (tasks) {
                            tasks.put(timer.start(), new CountDownLatch(taskDur));
                        }
                    }

                    synchronized (tasks) {
                        for (Map.Entry<Long, CountDownLatch> e : tasks.entrySet()) {
                            e.getValue().countDown();
                            if (e.getValue().getCount() == 0) {
                                timer.stop(e.getKey());
                                tasks.remove(e.getKey());
                            }
                        }
                    }
                })
                .blockLast();
    }
}
