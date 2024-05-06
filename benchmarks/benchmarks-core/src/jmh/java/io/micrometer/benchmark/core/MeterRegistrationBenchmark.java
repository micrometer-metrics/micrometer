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
package io.micrometer.benchmark.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
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
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
public class MeterRegistrationBenchmark {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(MeterRegistrationBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build();

        new Runner(opt).run();
    }

    MeterRegistry registry = new SimpleMeterRegistry();

    Meter.MeterProvider<Counter> counterMeterProvider = Counter.builder("jmh.existing").withRegistry(registry);

    @Setup
    public void setup() {
        registry.config()
            .commonTags("application", "abcservice", "az", "xyz", "environment", "production", "random-meta",
                    "random-meta");
        registry.counter("jmh.stale");
        registry.config().meterFilter(MeterFilter.acceptNameStartsWith("jmh"));
        registry.counter("jmh.existing", "k1", "v1");
    }

    @Benchmark
    @Warmup(iterations = 20)
    @Measurement(iterations = 200)
    @BenchmarkMode(Mode.SingleShotTime)
    public Meter registerNew() {
        return registry.counter("jmh.counter", "k1", "v1");
    }

    @Benchmark
    @Warmup(iterations = 20)
    @Measurement(iterations = 200)
    @BenchmarkMode(Mode.SingleShotTime)
    public Meter registerStale() {
        return registry.counter("jmh.stale");
    }

    @Benchmark
    public Meter registerExisting() {
        return registry.counter("jmh.existing", "k1", "v1");
    }

    @Benchmark
    public Meter registerExistingWithProvider() {
        return counterMeterProvider.withTag("k1", "v1");
    }

}
