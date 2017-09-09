/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.stats.quantile.GKQuantiles;
import io.micrometer.core.samples.utils.SampleRegistries;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TimerSample {
    public static void main(String[] args) {
        MeterRegistry registry = SampleRegistries.atlas();
        GKQuantiles quantiles = GKQuantiles.quantiles(0.95, 0.5).error(0.05).create();
        Timer timer = Timer.builder("timer").quantiles(quantiles).register(registry);

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
