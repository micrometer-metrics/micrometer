/*
 * Copyright 2022 VMware, Inc.
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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(4)
@State(Scope.Benchmark)
public class ObservationBenchmark {

    ObservationRegistry observationRegistry = ObservationRegistry.create();

    ObservationRegistry noopRegistry = ObservationRegistry.NOOP;

    @Setup
    public void setup() {
        this.observationRegistry.observationConfig().observationHandler(c -> false);
    }

    @Benchmark
    public void baseline() {
        // this method was intentionally left blank.
    }

    @Threads(1)
    @Benchmark
    public Observation observationWithoutThreadContention() {
        Observation observation = Observation.createNotStarted("test.obs", observationRegistry)
            .lowCardinalityKeyValue("abc", "123")
            .start();
        observation.stop();

        return observation;
    }

    @Benchmark
    public Observation observation() {
        Observation observation = Observation.createNotStarted("test.obs", observationRegistry)
            .lowCardinalityKeyValue("abc", "123")
            .start();
        observation.stop();

        return observation;
    }

    @Benchmark
    public Observation noopObservation() {
        // This might not measure anything if JIT figures it out that the registry is
        // always noop
        Observation observation = Observation.createNotStarted("test.obs", noopRegistry)
            .lowCardinalityKeyValue("abc", "123")
            .start();
        observation.stop();

        return observation;
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder().include(ObservationBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .build()).run();
    }

}
