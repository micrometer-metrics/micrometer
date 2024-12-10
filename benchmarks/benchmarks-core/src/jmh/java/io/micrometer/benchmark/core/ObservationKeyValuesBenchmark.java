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

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(4)
public class ObservationKeyValuesBenchmark {

    private static final KeyValues KEY_VALUES = KeyValues.of("key1", "value1", "key2", "value2", "key3", "value3",
            "key4", "value4", "key5", "value5");

    private final ObservationRegistry registry = ObservationRegistry.create();

    private final Observation.Context context = new TestContext();

    private final Observation observation = Observation.createNotStarted("jmh", () -> context, registry);

    @Benchmark
    @Group("contended")
    @GroupThreads(1)
    public Observation contendedWrite() {
        return write();
    }

    @Benchmark
    @Group("contended")
    @GroupThreads(1)
    public KeyValues contendedRead() {
        return read();
    }

    @Benchmark
    @Threads(1)
    public Observation uncontendedWrite() {
        return write();
    }

    @Benchmark
    @Threads(1)
    public KeyValues uncontendedRead() {
        return read();
    }

    private Observation write() {
        return observation.lowCardinalityKeyValues(KEY_VALUES);
    }

    private KeyValues read() {
        return observation.getContext().getLowCardinalityKeyValues();
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder().include(ObservationKeyValuesBenchmark.class.getSimpleName())
            .warmupIterations(3)
            .measurementIterations(5)
            .mode(Mode.SampleTime)
            .forks(1)
            .build();

        new Runner(options).run();
    }

    static class TestContext extends Observation.Context {

    }

}
