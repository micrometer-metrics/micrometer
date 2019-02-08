/**
 * Copyright 2018 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.benchmark.core;

import io.micrometer.core.instrument.Tags;
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
public class TagsBenchmark {
    @Threads(16)
    @Benchmark
    public void of() {
        Tags.of("key", "value", "key2", "value2", "key3", "value3", "key4", "value4", "key5", "value5");
    }

    @Threads(16)
    @Benchmark
    public void dotAnd() {
        Tags.of("key", "value").and("key2", "value2", "key3", "value3", "key4", "value4", "key5", "value5");
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(TagsBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
