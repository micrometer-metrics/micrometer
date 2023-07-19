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
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.samples.utils.SampleConfig;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class LongTaskTimerSample {

    public static void main(String[] args) {
        MeterRegistry registry = SampleConfig.myMonitoringSystem();
        LongTaskTimer timer = registry.more().longTaskTimer("longTaskTimer");

        RandomEngine r = new MersenneTwister64(0);
        Normal incomingRequests = new Normal(0, 1, r);
        Normal duration = new Normal(30, 50, r);

        AtomicInteger latencyForThisSecond = new AtomicInteger(duration.nextInt());
        Flux.interval(Duration.ofSeconds(1)).doOnEach(d -> latencyForThisSecond.set(duration.nextInt())).subscribe();

        final Map<LongTaskTimer.Sample, CountDownLatch> tasks = new ConcurrentHashMap<>();

        // the potential for an "incoming request" every 10 ms
        Flux.interval(Duration.ofSeconds(1)).doOnEach(d -> {
            if (incomingRequests.nextDouble() + 0.4 > 0 && tasks.isEmpty()) {
                int taskDur;
                while ((taskDur = duration.nextInt()) < 0)
                    ;
                synchronized (tasks) {
                    tasks.put(timer.start(), new CountDownLatch(taskDur));
                }
            }

            synchronized (tasks) {
                for (Map.Entry<LongTaskTimer.Sample, CountDownLatch> e : tasks.entrySet()) {
                    e.getValue().countDown();
                    if (e.getValue().getCount() == 0) {
                        e.getKey().stop();
                        tasks.remove(e.getKey());
                    }
                }
            }
        }).blockLast();
    }

}
