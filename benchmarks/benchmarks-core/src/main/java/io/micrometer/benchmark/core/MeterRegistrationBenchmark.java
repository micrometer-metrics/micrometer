/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MeterRegistrationBenchmark {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MeterRegistrationBenchmark.class.getSimpleName())
                .warmupIterations(2)
                .measurementIterations(5)
                .mode(Mode.SampleTime)
                .timeUnit(TimeUnit.SECONDS)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

    private int x = 923;
    private int y = 123;

    @Benchmark
    public int insert10_000() {
        MeterRegistry registry = new SimpleMeterRegistry();
        for (int i = 0; i < 10_000; i++) {
            registry.counter("my.counter", "k" + i, "v1");
        }
        return sum();
    }

    @Benchmark
    public int sum() {
        return x + y;
    }
}
