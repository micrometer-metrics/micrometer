/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.benchmark.prometheus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH Benchmarks for quantifying performance and allocations in {@link PrometheusMeterRegistry}.
 * <p>
 * Specifically benchmarks:
 * 1. Meter Creation: Batch registration of new meters into a fresh registry.
 * 2. Meter Scrape / Collection: Scraping pre-registered metrics.
 */
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class PrometheusRegistryBenchmark {

    public static final int CREATION_BATCH_SIZE = 500;

    @Param({"500"})
    private int scrapeMeterCount;

    private PrometheusMeterRegistry scrapeRegistry;
    private AtomicInteger gaugeValue;

    @Setup(Level.Trial)
    public void setupTrial() {
        scrapeRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        gaugeValue = new AtomicInteger(42);

        // Pre-register meters for the scrape benchmark (500 meter families with tags)
        for (int i = 0; i < scrapeMeterCount; i++) {
            String idSuffix = String.valueOf(i);
            Counter counter = scrapeRegistry.counter("benchmark_counter_" + idSuffix,
                    "region", "us-east-1", "env", "production", "service", "payment-service", "instance", idSuffix);
            counter.increment(i + 1);

            Gauge.builder("benchmark_gauge_" + idSuffix, gaugeValue, AtomicInteger::get)
                .tags("region", "us-east-1", "env", "production", "service", "payment-service", "instance", idSuffix)
                .register(scrapeRegistry);

            Timer timer = scrapeRegistry.timer("benchmark_timer_" + idSuffix,
                    "region", "us-east-1", "env", "production", "service", "payment-service", "instance", idSuffix);
            timer.record(i + 10, TimeUnit.MILLISECONDS);

            DistributionSummary summary = scrapeRegistry.summary("benchmark_summary_" + idSuffix,
                    "region", "us-east-1", "env", "production", "service", "payment-service", "instance", idSuffix);
            summary.record(i * 1.5);
        }
    }

    /**
     * Benchmark scenario 1: Meter creation and registration.
     * Uses batching (@OperationsPerInvocation) with 500 fresh meters per invocation to accurately
     * quantify per-meter creation latency and heap allocations while amortizing registry setup overhead.
     */
    @Benchmark
    @OperationsPerInvocation(CREATION_BATCH_SIZE)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void meterCreation(CreationState state, Blackhole bh) {
        PrometheusMeterRegistry registry = state.getRegistry();

        for (int i = 0; i < CREATION_BATCH_SIZE; i++) {
            String idSuffix = String.valueOf(i);
            Counter counter = registry.counter("created_counter_" + idSuffix,
                    "region", "us-east-1", "env", "production", "service", "auth-service", "instance", idSuffix);
            counter.increment();
            bh.consume(counter);
        }
    }

    /**
     * Benchmark scenario 2: Meter collection only (Phase 1 snapshot collection).
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public Object collect() {
        return scrapeRegistry.getPrometheusRegistry().scrape();
    }

    /**
     * Benchmark scenario 3: Full meter scrape (snapshot collection + text format serialization).
     * Evaluates scrape latency and allocations across registered meters.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public String scrape() {
        return scrapeRegistry.scrape();
    }

    @State(Scope.Thread)
    public static class CreationState {

        private PrometheusMeterRegistry registry;

        @Setup(Level.Invocation)
        public void setupInvocation() {
            registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        }

        public PrometheusMeterRegistry getRegistry() {
            return registry;
        }

    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
            .include(PrometheusRegistryBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
