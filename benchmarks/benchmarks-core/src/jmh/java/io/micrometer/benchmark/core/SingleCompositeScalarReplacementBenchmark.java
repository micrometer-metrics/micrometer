/*
 * Copyright 2025 VMware, Inc.
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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * This benchmark simulates an issue when "Scalar Replacement" optimization (see later)
 * using {@link CompositeMeterRegistry} can break.
 * <p>
 * This benchmark reproduces the issue using a {@link CompositeMeterRegistry} instance
 * that is empty at the beginning (no {@link MeterRegistry} is added to it) but after a
 * while a {@link MeterRegistry} is added to it. While the registry is empty, a
 * {@code Counter} is created and incremented several times.
 * <p>
 * In a real-world scenario, this can happen either when the user uses a
 * {@link CompositeMeterRegistry} or the global registry in {@code Metrics} but meter
 * registrations and recordings happen in the app before any {@link MeterRegistry} would
 * be added either to the global or the composite registry. This is an "invalid" scenario,
 * users should configure registries properly before they would be used.
 * <p>
 * The issue this benchmark reproduces is described in
 * <a href="https://github.com/micrometer-metrics/micrometer/issues/6811">#6811</a>:
 * {@code AbstractCompositeMeter} maintains a {@code Map} for its children. This map was
 * {@code Collections.emptyMap()} when an instance created and replaced to
 * {@code IdentityHashMap} once a {@link MeterRegistry} and Meters were added. This means
 * that in the scenario above, the Meters of the {@link CompositeMeterRegistry} first used
 * {@code Collections.emptyMap()} but then they used {@code IdentityHashMap}. This
 * apparently breaks "Scalar Replacement" (a JIT optimization that can eliminate
 * allocations). Before the fix in {@code AbstractCompositeMeter} (replacing
 * {@code Collections.emptyMap()} to an empty {@code IdentityHashMap})
 * {@link #composite()} did measure significant amount of allocations. After the fix,
 * allocations were eliminated.
 *
 * @see "https://github.com/micrometer-metrics/micrometer/issues/6811"
 * @see MultiCompositeScalarReplacementBenchmark
 */
@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class SingleCompositeScalarReplacementBenchmark {

    private SimpleMeterRegistry simpleMeterRegistry;

    private Counter compositeCounter;

    @Setup
    public void setup() {
        simpleMeterRegistry = new SimpleMeterRegistry();
        CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry();
        compositeCounter = compositeMeterRegistry.counter("compositeCounter");

        // Incrementing the counter before a MeterRegistry is added to the composite
        // is necessary to reproduce the issue. The amount of increments needed
        // before adding the registry might be different for you.
        for (int i = 0; i < 1_000; i++) {
            compositeCounter.increment();
        }
        compositeMeterRegistry.add(simpleMeterRegistry);

        System.out.println("\nMeters at setup:\n" + simpleMeterRegistry.getMetersAsString());
    }

    @TearDown
    public void tearDown() {
        System.out.println("\nMeters at tearDown:\n" + simpleMeterRegistry.getMetersAsString());
    }

    @Benchmark
    public void baseline() {
    }

    @Benchmark
    public void composite() {
        compositeCounter.increment();
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(SingleCompositeScalarReplacementBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
