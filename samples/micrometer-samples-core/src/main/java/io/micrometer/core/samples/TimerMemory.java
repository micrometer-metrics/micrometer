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
import io.micrometer.core.samples.utils.SampleRegistries;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.stream.Stream;

public class TimerMemory {

    public static void main(String[] args) throws InterruptedException {
        MeterRegistry registry = SampleRegistries.wavefront();

        Timer t = null;

        for (Integer i = 0; i < 80; i++) {
            t = Timer.builder("my.timer")
                .tag("index", i.toString())
                // .publishPercentileHistogram()
                .serviceLevelObjectives(Stream.of(1, 150, 300, 500, 900, 1000, 1200, 1500, 2000, 3000, 4000)
                    .map(Duration::ofMillis)
                    .toArray(Duration[]::new))
                .publishPercentiles(0.95)
                .percentilePrecision(1)
                .register(registry);
        }

        // Breakpoint somewhere after the first couple outputs to test pause detection
        // for (int i = 0; ; i = (i + 1) % 2000) {
        // Thread.sleep(2);
        // t.record(1, TimeUnit.MILLISECONDS);
        // if (i == 1000) {
        // t.takeSnapshot().outputSummary(System.out, 1e6);
        // }
        // }

        Flux.never().blockLast();
    }

}
