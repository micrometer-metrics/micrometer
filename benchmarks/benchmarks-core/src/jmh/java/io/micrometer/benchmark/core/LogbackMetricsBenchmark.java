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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

@Fork(1)
@Measurement(iterations = 2)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class LogbackMetricsBenchmark {

    Logger logger;

    @Param({ "false", "true" })
    boolean registerLogbackMetrics;

    MeterRegistry meterRegistry;

    LogbackMetrics logbackMetrics;

    @Setup
    public void setup() {
        meterRegistry = new SimpleMeterRegistry();
        if (registerLogbackMetrics) {
            logbackMetrics = new LogbackMetrics();
            logbackMetrics.bindTo(meterRegistry);
        }

        logger = LoggerFactory.getLogger(LogbackMetricsBenchmark.class);
    }

    @TearDown
    public void teardown() throws Exception {
        if (logbackMetrics != null) {
            logbackMetrics.close();
        }
    }

    @Benchmark
    public void logSomething() {
        logger.info("benchmark logging");
    }

}
