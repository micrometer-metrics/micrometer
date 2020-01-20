/**
 * Copyright 2020 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.benchmark.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class MeterRegistrationBenchmark {
    MeterRegistry registry = new SimpleMeterRegistry();

    /** Ensures we are testing a clean registry each time */
    @TearDown(Level.Invocation)
    public void clear() {
        registry.clear();
    }

    @Benchmark
    public void register_counter() {
        registry.counter("counter", "k", "v");
    }

    /** Ex. using a counter derived from a request property w/o an external counter cache */
    @Benchmark
    public void register_counter_redundant() {
        registry.counter("counter", "k", "v");
        registry.counter("counter", "k", "v");
    }

    @Benchmark
    public void register_counter_same_name_different_tags() {
        registry.counter("counter", "k1", "v");
        registry.counter("counter", "k2", "v");
    }

    @Benchmark
    public void register_counter_different_name_different_tags() {
        registry.counter("counter.1", "k1", "v");
        registry.counter("counter.2", "k2", "v");
    }

    // Convenience main entry-point
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .addProfiler("gc")
                .include(MeterRegistrationBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
