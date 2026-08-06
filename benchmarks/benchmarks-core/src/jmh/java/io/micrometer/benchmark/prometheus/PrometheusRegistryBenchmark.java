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
 * JMH Benchmarks for quantifying performance and allocations in
 * {@link PrometheusMeterRegistry}.
 * <p>
 * Specifically benchmarks: 1. Meter Creation: Batch registration of new meters into a
 * fresh registry. 2. Meter Scrape / Collection: Scraping pre-registered metrics.
 */
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class PrometheusRegistryBenchmark {

    public static final int CREATION_BATCH_SIZE = 500;

    private static final String[] COUNTER_NAMES = {
            "cache.gets", "executor.completed", "logback.events"
    };

    private static final String[] GAUGE_NAMES = {
            "jvm.memory.used", "system.cpu.usage"
    };

    private static final String[] TIMER_NAMES = {
            "http.server.requests", "http.client.requests", "db.statement"
    };

    private static final String[] SUMMARY_NAMES = {
            "http.server.response.size", "kafka.producer.record.size"
    };

    private static final String[] METHODS = { "GET", "POST", "PUT", "DELETE" };

    private static final String[] STATUSES = { "200", "201", "400", "404", "500" };

    private static final String[] OUTCOMES = { "SUCCESS", "CLIENT_ERROR", "SERVER_ERROR" };

    @Param({ "500" })
    private int scrapeMeterCount;

    private PrometheusMeterRegistry scrapeRegistry;

    private AtomicInteger gaugeValue;

    @Setup(Level.Trial)
    public void setupTrial() {
        scrapeRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        gaugeValue = new AtomicInteger(42);

        // Pre-register realistic meters for scrape benchmarks across 10 metric families with distinct types
        for (int i = 0; i < scrapeMeterCount; i++) {
            String uri = "/api/v1/endpoint_" + (i / 10);
            String method = METHODS[i % METHODS.length];
            String status = STATUSES[i % STATUSES.length];
            String outcome = OUTCOMES[i % OUTCOMES.length];

            if (i % 4 == 0) {
                String name = COUNTER_NAMES[(i / 4) % COUNTER_NAMES.length];
                Counter counter = scrapeRegistry.counter(name, "uri", uri, "method", method, "status", status, "outcome", outcome, "service", "payment-service");
                counter.increment(i + 1);
            } else if (i % 4 == 1) {
                String name = GAUGE_NAMES[(i / 4) % GAUGE_NAMES.length];
                Gauge.builder(name, gaugeValue, AtomicInteger::get)
                        .tags("uri", uri, "method", method, "status", status, "outcome", outcome, "service", "payment-service")
                        .register(scrapeRegistry);
            } else if (i % 4 == 2) {
                String name = TIMER_NAMES[(i / 4) % TIMER_NAMES.length];
                Timer timer = scrapeRegistry.timer(name, "uri", uri, "method", method, "status", status, "outcome", outcome, "service", "payment-service");
                timer.record(i + 10, TimeUnit.MILLISECONDS);
            } else {
                String name = SUMMARY_NAMES[(i / 4) % SUMMARY_NAMES.length];
                DistributionSummary summary = scrapeRegistry.summary(name, "uri", uri, "method", method, "status", status, "outcome", outcome, "service", "payment-service");
                summary.record(i * 1.5);
            }
        }
    }

    /**
     * Benchmark scenario 1: Meter creation and registration. Uses batching
     * (@OperationsPerInvocation) with 500 fresh meters per invocation across 10
     * realistic metric family names to accurately quantify per-meter creation
     * latency and heap allocations under realistic metric distributions.
     */
    @Benchmark
    @OperationsPerInvocation(CREATION_BATCH_SIZE)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void meterCreation(CreationState state, Blackhole bh) {
        PrometheusMeterRegistry registry = state.getRegistry();

        for (int i = 0; i < CREATION_BATCH_SIZE; i++) {
            String uri = "/api/v1/endpoint_" + (i / 10);
            String method = METHODS[i % METHODS.length];
            String status = STATUSES[i % STATUSES.length];
            String outcome = OUTCOMES[i % OUTCOMES.length];

            if (i % 4 == 0) {
                String name = COUNTER_NAMES[(i / 4) % COUNTER_NAMES.length];
                Counter counter = registry.counter(name, "uri", uri, "method", method, "status", status, "outcome", outcome, "service", "payment-service");
                counter.increment();
                bh.consume(counter);
            } else if (i % 4 == 1) {
                String name = GAUGE_NAMES[(i / 4) % GAUGE_NAMES.length];
                Gauge gauge = Gauge.builder(name, gaugeValue, AtomicInteger::get)
                        .tags("uri", uri, "method", method, "status", status, "outcome", outcome, "service", "payment-service")
                        .register(registry);
                bh.consume(gauge);
            } else if (i % 4 == 2) {
                String name = TIMER_NAMES[(i / 4) % TIMER_NAMES.length];
                Timer timer = registry.timer(name, "uri", uri, "method", method, "status", status, "outcome", outcome, "service", "payment-service");
                bh.consume(timer);
            } else {
                String name = SUMMARY_NAMES[(i / 4) % SUMMARY_NAMES.length];
                DistributionSummary summary = registry.summary(name, "uri", uri, "method", method, "status", status, "outcome", outcome, "service", "payment-service");
                bh.consume(summary);
            }
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
     * Benchmark scenario 3: Full meter scrape (snapshot collection + text format
     * serialization). Evaluates scrape latency and allocations across registered meters.
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
        new Runner(new OptionsBuilder().include(PrometheusRegistryBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
