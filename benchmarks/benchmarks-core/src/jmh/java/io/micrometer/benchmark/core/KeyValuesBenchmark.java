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
public class KeyValuesBenchmark {

    static final KeyValue[] orderedKeyValuesSet10 = new KeyValue[] { KeyValue.of("key0", "value"),
            KeyValue.of("key1", "value"), KeyValue.of("key2", "value"), KeyValue.of("key3", "value"),
            KeyValue.of("key4", "value"), KeyValue.of("key5", "value"), KeyValue.of("key6", "value"),
            KeyValue.of("key7", "value"), KeyValue.of("key8", "value"), KeyValue.of("key9", "value") };

    static final KeyValue[] orderedKeyValuesSet4 = new KeyValue[] { KeyValue.of("key0", "value"),
            KeyValue.of("key1", "value"), KeyValue.of("key2", "value"), KeyValue.of("key3", "value"), };

    static final KeyValue[] orderedKeyValuesSet2 = new KeyValue[] { KeyValue.of("key0", "value"),
            KeyValue.of("key1", "value"), };

    static final KeyValue[] unorderedKeyValuesSet10 = new KeyValue[] { KeyValue.of("key1", "value"),
            KeyValue.of("key2", "value"), KeyValue.of("key3", "value"), KeyValue.of("key4", "value"),
            KeyValue.of("key5", "value"), KeyValue.of("key6", "value"), KeyValue.of("key7", "value"),
            KeyValue.of("key8", "value"), KeyValue.of("key9", "value"), KeyValue.of("key0", "value") };

    static final KeyValue[] unorderedKeyValuesSet4 = new KeyValue[] { KeyValue.of("key1", "value"),
            KeyValue.of("key2", "value"), KeyValue.of("key3", "value"), KeyValue.of("key0", "value"), };

    static final KeyValue[] unorderedKeyValuesSet2 = new KeyValue[] { KeyValue.of("key1", "value"),
            KeyValue.of("key0", "value") };

    @Benchmark
    public KeyValues KeyValuesOfOrderedKeyValuesSet10() {
        return KeyValues.of(orderedKeyValuesSet10);
    }

    @Benchmark
    public KeyValues KeyValuesOfOrderedKeyValuesSet4() {
        return KeyValues.of(orderedKeyValuesSet4);
    }

    @Benchmark
    public KeyValues KeyValuesOfOrderedKeyValuesSet2() {
        return KeyValues.of(orderedKeyValuesSet2);
    }

    @Benchmark
    public KeyValues KeyValuesOfUnorderedKeyValuesSet10() {
        return KeyValues.of(unorderedKeyValuesSet10);
    }

    @Benchmark
    public KeyValues KeyValuesOfUnorderedKeyValuesSet4() {
        return KeyValues.of(unorderedKeyValuesSet4);
    }

    @Benchmark
    public KeyValues KeyValuesOfUnorderedKeyValuesSet2() {
        return KeyValues.of(unorderedKeyValuesSet2);
    }

    @Benchmark
    public KeyValues of() {
        return KeyValues.of("key", "value", "key2", "value2", "key3", "value3", "key4", "value4", "key5", "value5");
    }

    @Benchmark
    public KeyValues dotAnd() {
        return KeyValues.of("key", "value").and("key2", "value2", "key3", "value3", "key4", "value4", "key5", "value5");
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(KeyValuesBenchmark.class.getSimpleName()).build();
        new Runner(opt).run();
    }

}
