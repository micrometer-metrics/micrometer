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

    private static final String[] URIS = {
            "/api/v1/users", "/api/v1/orders/{id}", "/api/v1/products", "/health", "/api/v1/checkout"
    };

    private static final String[] METHODS = { "GET", "POST", "PUT", "DELETE" };

    private static final String[] STATUSES = { "200", "201", "400", "404", "500" };

    private static final String[] OUTCOMES = { "SUCCESS", "CLIENT_ERROR", "SERVER_ERROR" };

    private static final String[] EXCEPTIONS = { "none", "IllegalArgumentException", "IllegalStateException" };

    private static final String[] CLIENT_NAMES = { "inventory-service", "payment-gateway" };

    private static final String[] DB_STATEMENTS = { "select-user", "insert-order", "update-stock" };

    private static final String[] LOG_LEVELS = { "info", "warn", "error" };

    private static final String[] CACHE_NAMES = { "users", "products", "orders" };

    @Param({ "500" })
    private int scrapeMeterCount;

    private PrometheusMeterRegistry scrapeRegistry;

    private AtomicInteger gaugeValue;

    @Setup(Level.Trial)
    public void setupTrial() {
        scrapeRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        gaugeValue = new AtomicInteger(42);

        // System gauges registered once during setup
        Gauge.builder("jvm.memory.used", gaugeValue, AtomicInteger::get)
                .tags("area", "heap", "id", "G1 Eden Space")
                .register(scrapeRegistry);
        Gauge.builder("jvm.memory.used", gaugeValue, AtomicInteger::get)
                .tags("area", "heap", "id", "G1 Old Gen")
                .register(scrapeRegistry);
        Gauge.builder("system.cpu.usage", gaugeValue, AtomicInteger::get)
                .register(scrapeRegistry);

        // Pre-register realistic meters for scrape benchmarks (~65% Timers, ~30% Counters, ~5% Summaries)
        for (int i = 0; i < scrapeMeterCount; i++) {
            int modulo = i % 20;
            if (modulo < 13) {
                // Timer: http.server.requests, http.client.requests, db.statement
                if (modulo < 8) {
                    scrapeRegistry.timer("http.server.requests",
                            "uri", URIS[i % URIS.length],
                            "method", METHODS[i % METHODS.length],
                            "status", STATUSES[i % STATUSES.length],
                            "outcome", OUTCOMES[i % OUTCOMES.length],
                            "exception", EXCEPTIONS[i % EXCEPTIONS.length]
                    ).record(i + 10, TimeUnit.MILLISECONDS);
                } else if (modulo < 11) {
                    scrapeRegistry.timer("http.client.requests",
                            "clientName", CLIENT_NAMES[i % CLIENT_NAMES.length],
                            "uri", URIS[i % URIS.length],
                            "method", METHODS[i % METHODS.length],
                            "status", STATUSES[i % STATUSES.length],
                            "outcome", OUTCOMES[i % OUTCOMES.length]
                    ).record(i + 5, TimeUnit.MILLISECONDS);
                } else {
                    scrapeRegistry.timer("db.statement",
                            "name", DB_STATEMENTS[i % DB_STATEMENTS.length],
                            "status", "success"
                    ).record(i + 1, TimeUnit.MILLISECONDS);
                }
            } else if (modulo < 19) {
                // Counter: logback.events, cache.gets
                if (modulo < 16) {
                    scrapeRegistry.counter("logback.events", "level", LOG_LEVELS[i % LOG_LEVELS.length]).increment();
                } else {
                    scrapeRegistry.counter("cache.gets",
                            "cache", CACHE_NAMES[i % CACHE_NAMES.length],
                            "result", (i % 2 == 0) ? "hit" : "miss"
                    ).increment();
                }
            } else {
                // DistributionSummary: http.server.response.size
                scrapeRegistry.summary("http.server.response.size",
                        "method", METHODS[i % METHODS.length],
                        "status", STATUSES[i % STATUSES.length]
                ).record(i * 128.0);
            }
        }
    }

    /**
     * Benchmark scenario 1: Meter creation and registration. Uses batching
     * (@OperationsPerInvocation) with 500 fresh meters per invocation using a realistic
     * mix of web instrumentation (65% Timers, 30% Counters, 5% Summaries) with matching tags.
     */
    @Benchmark
    @OperationsPerInvocation(CREATION_BATCH_SIZE)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void meterCreation(CreationState state, Blackhole bh) {
        PrometheusMeterRegistry registry = state.getRegistry();

        for (int i = 0; i < CREATION_BATCH_SIZE; i++) {
            int modulo = i % 20;
            if (modulo < 13) {
                if (modulo < 8) {
                    Timer timer = registry.timer("http.server.requests",
                            "uri", URIS[i % URIS.length],
                            "method", METHODS[i % METHODS.length],
                            "status", STATUSES[i % STATUSES.length],
                            "outcome", OUTCOMES[i % OUTCOMES.length],
                            "exception", EXCEPTIONS[i % EXCEPTIONS.length]);
                    bh.consume(timer);
                } else if (modulo < 11) {
                    Timer timer = registry.timer("http.client.requests",
                            "clientName", CLIENT_NAMES[i % CLIENT_NAMES.length],
                            "uri", URIS[i % URIS.length],
                            "method", METHODS[i % METHODS.length],
                            "status", STATUSES[i % STATUSES.length],
                            "outcome", OUTCOMES[i % OUTCOMES.length]);
                    bh.consume(timer);
                } else {
                    Timer timer = registry.timer("db.statement",
                            "name", DB_STATEMENTS[i % DB_STATEMENTS.length],
                            "status", "success");
                    bh.consume(timer);
                }
            } else if (modulo < 19) {
                if (modulo < 16) {
                    Counter counter = registry.counter("logback.events", "level", LOG_LEVELS[i % LOG_LEVELS.length]);
                    bh.consume(counter);
                } else {
                    Counter counter = registry.counter("cache.gets",
                            "cache", CACHE_NAMES[i % CACHE_NAMES.length],
                            "result", (i % 2 == 0) ? "hit" : "miss");
                    bh.consume(counter);
                }
            } else {
                DistributionSummary summary = registry.summary("http.server.response.size",
                        "method", METHODS[i % METHODS.length],
                        "status", STATUSES[i % STATUSES.length]);
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
