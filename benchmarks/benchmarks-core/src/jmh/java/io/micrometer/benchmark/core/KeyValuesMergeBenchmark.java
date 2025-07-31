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
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@Fork(1)
@Measurement(iterations = 2)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class KeyValuesMergeBenchmark {

    static final KeyValues left = KeyValues.of("key", "value", "key2", "value2", "key6", "value6", "key7", "value7",
            "key8", "value8", "keyA", "valueA", "keyC", "valueC", "keyE", "valueE", "keyF", "valueF", "keyG", "valueG",
            "keyH", "valueH");

    static final KeyValues right = KeyValues.of("key", "value", "key1", "value1", "key2", "value2", "key3", "value3",
            "key4", "value4", "key5", "value5", "keyA", "valueA", "keyB", "valueB", "keyD", "valueD");

    @Benchmark
    public KeyValues mergeKeyValues() {
        return left.and(right);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(KeyValuesMergeBenchmark.class.getSimpleName()).build();
        new Runner(opt).run();
    }

}
