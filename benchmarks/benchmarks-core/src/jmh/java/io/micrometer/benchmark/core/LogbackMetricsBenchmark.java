/*
 * Copyright 2023 VMware, Inc.
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

import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for {@link LogbackMetrics}.
 */
@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 2)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class LogbackMetricsBenchmark {

    private SimpleMeterRegistry meterRegistry;

    private LogbackMetrics logbackMetrics;

    private Logger unfilteredLogger;

    private Logger filteredLogger;

    @Param({ "false", "true" })
    private boolean registerLogbackMetrics;

    @Setup
    public void setup() {
        meterRegistry = new SimpleMeterRegistry();
        if (registerLogbackMetrics) {
            logbackMetrics = new LogbackMetrics();
            logbackMetrics.bindTo(meterRegistry);
        }

        unfilteredLogger = LoggerFactory.getLogger("unfilteredLogger");
        filteredLogger = LoggerFactory.getLogger("filteredLogger");
        // initialize logback
        unfilteredLogger.info("setup");
        filteredLogger.info("setup");
        System.out.println("\nMetrics at setup:\n" + meterRegistry.getMetersAsString());
    }

    @TearDown
    public void tearDown() {
        if (logbackMetrics != null) {
            logbackMetrics.close();
        }
        System.out.println("\nMetrics at tearDown:\n" + meterRegistry.getMetersAsString());
    }

    /**
     * Benchmark of unfiltered log events. These events are recorded as metrics.
     */
    @Benchmark
    public void unfiltered() {
        unfilteredLogger.info("something");
    }

    /**
     * Benchmark of filtered log events. These events are NOT recorded as metrics.
     */
    @Benchmark
    public void filtered() {
        filteredLogger.info("somethingFiltered");
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(LogbackMetricsBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
