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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.samples.utils.SampleConfig;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class LatencySample {

    public static void main(String[] args) {
        new LatencySample().run();
    }

    private MeterRegistry registry = SampleConfig.myMonitoringSystem();

    private Random r = new Random();

    private Timer timer = Timer.builder("request").publishPercentileHistogram().register(registry);

    void run() {
        Flux.interval(Duration.ofMillis(1)).doOnEach(n -> recordGaussian(10)).subscribe();

        Flux.interval(Duration.ofSeconds(1)).doOnEach(n -> recordGaussian(300)).blockLast();
    }

    private void recordGaussian(long center) {
        timer.record(simulatedLatency(center), TimeUnit.MILLISECONDS);
    }

    private long simulatedLatency(long center) {
        return (long) (r.nextGaussian() * 10) + center;
    }

}
