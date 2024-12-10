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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ObservationKeyValuesBenchmark {

    private static final KeyValues KEY_VALUES = KeyValues.of("key1", "value1", "key2", "value2", "key3", "value3",
            "key4", "value4", "key5", "value5");

    private static final KeyValue KEY_VALUE = KeyValue.of("testKey", "testValue");

    @Benchmark
    public void noopBaseline() {
    }

    @Benchmark
    public Observation.Context contextBaseline() {
        return new TestContext().addLowCardinalityKeyValues(KEY_VALUES);
    }

    @Benchmark
    public Observation.Context putKeyValue() {
        return new TestContext().addLowCardinalityKeyValues(KEY_VALUES).addLowCardinalityKeyValue(KEY_VALUE);
    }

    @Benchmark
    public KeyValues readKeyValues() {
        return new TestContext().addLowCardinalityKeyValues(KEY_VALUES).getLowCardinalityKeyValues();
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder().include(ObservationKeyValuesBenchmark.class.getSimpleName())
            .warmupIterations(5)
            .measurementIterations(10)
            .mode(Mode.SampleTime)
            .forks(1)
            .build();

        new Runner(options).run();
    }

    static class TestContext extends Observation.Context {

    }

}
