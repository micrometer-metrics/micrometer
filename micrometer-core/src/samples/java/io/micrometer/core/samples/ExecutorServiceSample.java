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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.ExecutorServiceMetrics;
import io.micrometer.core.samples.utils.SampleRegistries;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;

public class ExecutorServiceSample {
    public static void main(String[] args) {
        MeterRegistry registry = SampleRegistries.prometheus();
        ScheduledExecutorService es = Executors.newSingleThreadScheduledExecutor();
        new ExecutorServiceMetrics(es, "executor.sample", emptyList()).bindTo(registry);

        es.scheduleWithFixedDelay(() -> Mono.delay(Duration.ofMillis(20)).block(), 0,
                10, TimeUnit.MILLISECONDS);

        while(true) {}
    }
}
