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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.samples.utils.SampleConfig;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Designed to test the maximum throughput of various timer configurations
 */
public class TimerMaximumThroughputSample {

    public static void main(String[] args) {
        MeterRegistry registry = SampleConfig.myMonitoringSystem();
        Timer timer = Timer.builder("timer")
            .publishPercentileHistogram()
            // .publishPercentiles(0.5, 0.95, 0.99)
            .serviceLevelObjectives(Duration.ofMillis(275), Duration.ofMillis(300), Duration.ofMillis(500))
            .distributionStatisticExpiry(Duration.ofSeconds(10))
            .distributionStatisticBufferLength(3)
            .register(registry);

        RandomEngine r = new MersenneTwister64(0);
        Normal duration = new Normal(250, 50, r);

        AtomicInteger latencyForThisSecond = new AtomicInteger(duration.nextInt());
        Flux.interval(Duration.ofSeconds(1)).doOnEach(d -> latencyForThisSecond.set(duration.nextInt())).subscribe();

        Stream<Integer> infiniteStream = Stream.iterate(0, i -> (i + 1) % 1000);
        Flux.fromStream(infiniteStream)
            .parallel(4)
            .runOn(Schedulers.parallel())
            .doOnEach(d -> timer.record(latencyForThisSecond.get(), TimeUnit.MILLISECONDS))
            .subscribe();

        Flux.never().blockLast();
    }

}
