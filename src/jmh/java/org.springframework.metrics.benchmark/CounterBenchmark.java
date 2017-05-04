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
package org.springframework.metrics.benchmark;

import com.netflix.spectator.api.Counter;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class CounterBenchmark extends AbstractCollectorBenchmark {

    private Counter spectatorCounter;
    private com.codahale.metrics.Counter dropwizardCounter;

    @Setup
    public void setup() {
        spectatorCounter = spectatorRegistry.counter("count");
        dropwizardCounter = dropwizardRegistry.counter("count");
    }

    @Benchmark
    public void spectatorCounter() {
        spectatorCounter.increment();
    }

    @Benchmark
    public void bootCounter() {
        bootCounterService.increment("count");
    }

    @Benchmark
    public void prometheusCounter() {
        prometheusCounter.inc();
    }

    @Benchmark
    public void datadogStatsdCounter() {
        statsd.incrementCounter("count");
    }

    @Benchmark
    public void dropwizardCounter() {
        dropwizardCounter.inc();
    }
}
