/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.benchmark.compare;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author John Karp
 */
@Fork(1)
@Measurement(iterations = 2)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(16)
public class CompareCountersWithOtherLibraries {

    @State(Scope.Benchmark)
    public static class DropwizardState {

        com.codahale.metrics.MetricRegistry registry;

        com.codahale.metrics.Counter counter;

        @Setup(Level.Trial)
        public void setup() {
            registry = new com.codahale.metrics.MetricRegistry();
            counter = registry.counter("counter");
        }

        @TearDown(Level.Trial)
        public void tearDown(Blackhole hole) {
            hole.consume(counter.getCount());
        }

    }

    @State(Scope.Benchmark)
    public static class MicrometerState {

        io.micrometer.core.instrument.MeterRegistry registry;

        io.micrometer.core.instrument.Counter counter;

        io.micrometer.core.instrument.Counter counterWithTags;

        @Setup(Level.Trial)
        public void setup() {
            registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            counter = registry.counter("untagged");
            counterWithTags = registry.counter("tagged", "key1", "value1", "key2", "value2");
        }

        @TearDown(Level.Trial)
        public void tearDown(Blackhole hole) {
            for (io.micrometer.core.instrument.Meter m : registry.getMeters()) {
                if (m instanceof io.micrometer.core.instrument.Counter) {
                    hole.consume(((io.micrometer.core.instrument.Counter) m).count());
                }
            }
        }

    }

    @State(Scope.Benchmark)
    public static class Dropwizard5State {

        io.dropwizard.metrics5.MetricRegistry registry;

        io.dropwizard.metrics5.Counter counter;

        io.dropwizard.metrics5.Counter counterWithTags;

        @Setup(Level.Trial)
        public void setup() {
            registry = new io.dropwizard.metrics5.MetricRegistry();
            counter = registry.counter("untagged");
            Map<String, String> tags = new HashMap<>();
            tags.put("key1", "value1");
            tags.put("key2", "value2");
            counterWithTags = registry.counter(new io.dropwizard.metrics5.MetricName("tagged", tags));
        }

        @TearDown(Level.Trial)
        public void tearDown(Blackhole hole) {
            for (io.dropwizard.metrics5.Counter c : registry.getCounters().values()) {
                hole.consume(c.getCount());
            }
        }

    }

    @State(Scope.Benchmark)
    public static class PrometheusState {

        io.prometheus.metrics.core.metrics.Counter counter;

        io.prometheus.metrics.core.metrics.Counter counterWithTags;

        @Setup(Level.Trial)
        public void setup() {
            counter = io.prometheus.metrics.core.metrics.Counter.builder().name("counter").help("A counter").register();
            counterWithTags = io.prometheus.metrics.core.metrics.Counter.builder()
                .name("counter")
                .help("Counter with two tags declared")
                .labelNames("key1", "key2")
                .register();
        }

    }

    // @Benchmark
    public void dropwizard5Counter(Dropwizard5State state) {
        state.counter.inc();
    }

    // @Benchmark
    public void dropwizard5CounterFixedTags(Dropwizard5State state) {
        state.counterWithTags.inc();
    }

    // @Benchmark
    public void dropwizard5CounterTags(Dropwizard5State state) {
        Map<String, String> tags = new HashMap<>();
        tags.put("key1", "value1");
        tags.put("key2", "value2");
        state.registry.counter(new io.dropwizard.metrics5.MetricName("tagged", tags)).inc();
    }

    // @Benchmark
    public void micrometerCounter(MicrometerState state) {
        state.counter.increment();
    }

    @Benchmark
    public void micrometerCounterTags(MicrometerState state) {
        state.registry.counter("dynamicTags", "key1", "value1", "key2", "value2").increment();
    }

    // @Benchmark
    public void micrometerCounterFixedTags(MicrometerState state) {
        state.counterWithTags.increment();
    }

    // @Benchmark
    public void prometheusCounter(PrometheusState state) {
        state.counter.inc();
    }

    @Benchmark
    public void prometheusCounterWithTags(PrometheusState state) {
        state.counterWithTags.labelValues("value1", "value2").inc();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(CompareCountersWithOtherLibraries.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build();
        new Runner(opt).run();
    }

}
