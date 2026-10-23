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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class LoggerExclusionBenchmark {

    private SimpleMeterRegistry meterRegistry;

    private LogbackMetrics logbackMetrics;

    private LoggerContext loggerContext;

    private Logger logger;

    @Param({ "none", "empty", "small" })
    private String exclusionMode;

    @Setup
    public void setup() {
        meterRegistry = new SimpleMeterRegistry();
        loggerContext = new LoggerContext();
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);

        switch (exclusionMode) {
            case "none":
                logbackMetrics = new LogbackMetrics(Collections.emptyList(), loggerContext);
                break;
            case "empty":
                logbackMetrics = new LogbackMetrics(Collections.emptyList(), loggerContext, Collections.emptySet());
                break;
            case "small":
                logbackMetrics = new LogbackMetrics(Collections.emptyList(), loggerContext,
                        Set.of("com.example.foo", "com.example.bar"));
                break;
        }
        logbackMetrics.bindTo(meterRegistry);

        logger = loggerContext.getLogger("com.example.app.service");
    }

    @TearDown
    public void tearDown() {
        if (logbackMetrics != null) {
            logbackMetrics.close();
        }
    }

    @Benchmark
    public void logEvent() {
        logger.info("benchmark message");
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(LoggerExclusionBenchmark.class.getSimpleName()).build()).run();
    }

}
