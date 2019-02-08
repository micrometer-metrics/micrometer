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
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class TimerBenchmark {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TimerBenchmark.class.getSimpleName())
                .warmupIterations(5)
                .measurementIterations(10)
                .mode(Mode.SampleTime)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

    private MeterRegistry registry;
    private Timer timer;

    int x = 923;
    int y = 123;

    @Setup
    public void setup() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        timer = registry.timer("timer");
    }

    @Benchmark
    public int sumTimedWithSupplier() {
        return timer.record(this::sum);
    }

    @Benchmark
    public int sumTimedWithSample() {
        Timer.Sample sample = Timer.start(registry);
        int sum = sum();
        sample.stop(timer);
        return sum;
    }

    @Benchmark
    public int sumTimedWithRegistryLookup() {
        return registry.timer("timer").record(this::sum);
    }

    @Benchmark
    public int sum() {
        return x + y;
    }
}
