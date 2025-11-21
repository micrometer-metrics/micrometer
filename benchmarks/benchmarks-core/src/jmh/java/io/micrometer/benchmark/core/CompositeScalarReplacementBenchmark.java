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
 * This benchmark reproduces the issue using two {@link CompositeMeterRegistry} instances:
 * one of them is empty, the other one is not (a {@link MeterRegistry} is added to it).
 * <p>
 * For example, the user uses a {@link CompositeMeterRegistry} that is injected into the
 * components of the application but a component somewhere (that they may or may not
 * control) uses {@code Metrics}, a static, global {@link CompositeMeterRegistry}. The
 * user don't care about the global registry and don't add any {@link MeterRegistry} to
 * it.
 * <p>
 * Another similar scenario (that this benchmark does not simulate) is when the user uses
 * both a {@link CompositeMeterRegistry} (that is injected into the components of the
 * application) and the global registry in {@code Metrics} but meter registrations and
 * recordings happen in the app before any {@link MeterRegistry} would be added either to
 * the global or the composite registry. This is an "invalid" scenario, users should
 * configure registries properly before they would be used but this scenario causes the
 * same issue that the previous one does. This is possible even with one registry, if
 * "enough" recordings happen before any {@link MeterRegistry} would be added to the
 * composite/global.
 * <p>
 * The issue this benchmark reproduces is described in
 * <a href="https://github.com/micrometer-metrics/micrometer/issues/6811">#6811</a>:
 * {@code AbstractCompositeMeter} maintains a {@code Map} for its children. This map was
 * {@code Collections.emptyMap()} when an instance created and replaced to
 * {@code IdentityHashMap} as Meters were added. This means that in the scenarios above,
 * the Meters of the non-empty {@link CompositeMeterRegistry} use {@code IdentityHashMap}
 * and Meters of the empty one use {@code Collections.emptyMap()}. This apparently breaks
 * "Scalar Replacement" (a JIT optimization that can eliminate allocations). Before the
 * fix in {@code AbstractCompositeMeter} (replacing {@code Collections.emptyMap()} to an
 * empty {@code IdentityHashMap}) {@link #compositeBaseline()} and
 * {@link #emptyCompositeBaseline()} did not measure significant amount of allocations
 * (virtually 0) but {@link #compositeAndEmptyComposite()} did. After the fix, allocations
 * were eliminated.
 *
 * @see "https://github.com/micrometer-metrics/micrometer/issues/6811"
 */
@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class CompositeScalarReplacementBenchmark {

    private SimpleMeterRegistry simpleMeterRegistry;

    private Counter compositeCounter;

    private Counter emptyCompositeCounter;

    @Setup
    public void setup() {
        simpleMeterRegistry = new SimpleMeterRegistry();

        MeterRegistry composite = new CompositeMeterRegistry().add(simpleMeterRegistry);
        compositeCounter = composite.counter("compositeCounter");

        MeterRegistry emptyComposite = new CompositeMeterRegistry();
        emptyCompositeCounter = emptyComposite.counter("emptyCompositeCounter");

        System.out.println("\nMeters at setup:\n" + simpleMeterRegistry.getMetersAsString());
    }

    @TearDown
    public void tearDown() {
        System.out.println("\nMeters at tearDown:\n" + simpleMeterRegistry.getMetersAsString());
    }

    @Benchmark
    public void compositeBaseline() {
        compositeCounter.increment();
    }

    @Benchmark
    public void emptyCompositeBaseline() {
        emptyCompositeCounter.increment();
    }

    @Benchmark
    public void compositeAndEmptyComposite() {
        compositeCounter.increment();
        emptyCompositeCounter.increment();
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(CompositeScalarReplacementBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
