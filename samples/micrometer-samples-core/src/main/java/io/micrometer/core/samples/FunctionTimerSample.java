/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.samples.utils.SampleConfig;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FunctionTimerSample {

    // For Atlas:
    // http://localhost:7101/api/v1/graph?q=name,ftimer,:eq,:dist-avg,name,timer,:eq,:dist-avg,1,:axis&s=e-5m&l=0
    public static void main(String[] args) {
        MeterRegistry registry = SampleConfig.myMonitoringSystem();

        Timer timer = Timer.builder("timer").publishPercentiles(0.5, 0.95).register(registry);

        Object placeholder = new Object();
        AtomicLong totalTimeNanos = new AtomicLong();
        AtomicLong totalCount = new AtomicLong();

        FunctionTimer
            .builder("ftimer", placeholder, p -> totalCount.get(), p -> totalTimeNanos.get(), TimeUnit.NANOSECONDS)
            .register(registry);

        RandomEngine r = new MersenneTwister64(0);
        Normal incomingRequests = new Normal(0, 1, r);
        Normal duration = new Normal(250, 50, r);

        AtomicInteger latencyForThisSecond = new AtomicInteger(duration.nextInt());
        Flux.interval(Duration.ofSeconds(1)).doOnEach(d -> latencyForThisSecond.set(duration.nextInt())).subscribe();

        // the potential for an "incoming request" every 10 ms
        Flux.interval(Duration.ofMillis(10)).doOnEach(d -> {
            if (incomingRequests.nextDouble() + 0.4 > 0) {
                // pretend the request took some amount of time, such that the time is
                // distributed normally with a mean of 250ms
                timer.record(latencyForThisSecond.get(), TimeUnit.MILLISECONDS);
                totalCount.incrementAndGet();
                totalTimeNanos
                    .addAndGet((long) TimeUtils.millisToUnit(latencyForThisSecond.get(), TimeUnit.NANOSECONDS));
            }
        }).blockLast();
    }

}
