/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.benchmark.core;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MeterRemovalBenchmark {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(MeterRemovalBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build();

        new Runner(opt).run();
    }

    @Param({ "10000", "100000" })
    int meterCount;

    MeterRegistry registry = new SimpleMeterRegistry();

    @Setup
    public void setup() {
        for (int i = 0; i < meterCount; i++) {
            registry.counter("counter", "key", String.valueOf(i));
        }
    }

    /**
     * Benchmark the time to remove one meter from a registry with many meters. This uses
     * the single shot mode because otherwise it would measure the time to remove a meter
     * not in the registry after the first call, and that is not what we want to measure.
     */
    @Benchmark
    @Warmup(iterations = 100)
    @Measurement(iterations = 500)
    @BenchmarkMode(Mode.SingleShotTime)
    public Meter remove() {
        return registry.remove(registry.counter("counter", "key", String.valueOf(meterCount / 2)));
    }

}
