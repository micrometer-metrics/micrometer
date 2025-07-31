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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Fork(1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class GaugeBenchmark {

    private MeterRegistry registry;

    @Setup
    public void setup() {
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.gauge("test.gauge", new AtomicInteger());
        // emits warn because of double registration
        registry.gauge("test.gauge", new AtomicInteger());
        // emits debug because of double registration and keeps emitting debug from now on
        registry.gauge("test.gauge", new AtomicInteger());
    }

    @Benchmark
    public AtomicInteger baseline() {
        return new AtomicInteger();
    }

    @Benchmark
    public AtomicInteger gaugeReRegistrationWithoutBuilder() {
        return registry.gauge("test.gauge", new AtomicInteger());
    }

    @Benchmark
    public Gauge gaugeReRegistrationWithBuilder() {
        return Gauge.builder("test.gauge", new AtomicInteger(), AtomicInteger::doubleValue).register(registry);
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(GaugeBenchmark.class.getSimpleName()).build()).run();
    }

}
