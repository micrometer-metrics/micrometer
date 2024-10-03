/*
 * Copyright 2018 VMware, Inc.
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

import io.micrometer.core.instrument.Tag;
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

    static final Tag[] orderedTagsSet10 = new Tag[] { Tag.of("key0", "value"), Tag.of("key1", "value"),
            Tag.of("key2", "value"), Tag.of("key3", "value"), Tag.of("key4", "value"), Tag.of("key5", "value"),
            Tag.of("key6", "value"), Tag.of("key7", "value"), Tag.of("key8", "value"), Tag.of("key9", "value") };

    static final Tag[] orderedTagsSet4 = new Tag[] { Tag.of("key0", "value"), Tag.of("key1", "value"),
            Tag.of("key2", "value"), Tag.of("key3", "value"), };

    static final Tag[] orderedTagsSet2 = new Tag[] { Tag.of("key0", "value"), Tag.of("key1", "value"), };

    static final Tag[] unorderedTagsSet10 = new Tag[] { Tag.of("key1", "value"), Tag.of("key2", "value"),
            Tag.of("key3", "value"), Tag.of("key4", "value"), Tag.of("key5", "value"), Tag.of("key6", "value"),
            Tag.of("key7", "value"), Tag.of("key8", "value"), Tag.of("key9", "value"), Tag.of("key0", "value") };

    static final Tag[] unorderedTagsSet4 = new Tag[] { Tag.of("key1", "value"), Tag.of("key2", "value"),
            Tag.of("key3", "value"), Tag.of("key0", "value"), };

    static final Tag[] unorderedTagsSet2 = new Tag[] { Tag.of("key1", "value"), Tag.of("key0", "value") };

    @Benchmark
    public Tags tagsOfOrderedTagsSet10() {
        return Tags.of(orderedTagsSet10);
    }

    @Benchmark
    public Tags tagsOfOrderedTagsSet4() {
        return Tags.of(orderedTagsSet4);
    }

    @Benchmark
    public Tags tagsOfOrderedTagsSet2() {
        return Tags.of(orderedTagsSet2);
    }

    @Benchmark
    public Tags tagsOfUnorderedTagsSet10() {
        return Tags.of(unorderedTagsSet10);
    }

    @Benchmark
    public Tags tagsOfUnorderedTagsSet4() {
        return Tags.of(unorderedTagsSet4);
    }

    @Benchmark
    public Tags tagsOfUnorderedTagsSet2() {
        return Tags.of(unorderedTagsSet2);
    }

    @Benchmark
    public Tags of() {
        return Tags.of("key", "value", "key2", "value2", "key3", "value3", "key4", "value4", "key5", "value5");
    }

    @Benchmark
    public Tags dotAnd() {
        return Tags.of("key", "value").and("key2", "value2", "key3", "value3", "key4", "value4", "key5", "value5");
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(TagsBenchmark.class.getSimpleName()).build();
        new Runner(opt).run();
    }

}
