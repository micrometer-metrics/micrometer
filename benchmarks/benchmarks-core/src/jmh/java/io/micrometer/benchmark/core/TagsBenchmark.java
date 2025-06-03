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
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TagsBenchmark {

    @Fork(1)
    @Measurement(iterations = 2)
    @Warmup(iterations = 2)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public static class TagsOfBenchmark {

        @Benchmark
        public Tags ofStringVarargs() {
            return Tags.of("key", "value", "key2", "value2", "key3", "value3", "key4", "value4", "key5", "value5");
        }

        @Benchmark
        public Tags ofTagVarargs() {
            return Tags.of(Tag.of("key", "value"), Tag.of("key2", "value2"), Tag.of("key3", "value3"),
                    Tag.of("key4", "value4"), Tag.of("key5", "value5"));
        }

        @Benchmark
        public Tags ofTags() {
            return Tags
                .of(Tags.of("key", "value", "key2", "value2", "key3", "value3", "key4", "value4", "key5", "value5"));
        }

        @Benchmark
        public Tags ofArrayList() {
            List<Tag> tags = new ArrayList<>(5);
            tags.add(Tag.of("key", "value"));
            tags.add(Tag.of("key2", "value2"));
            tags.add(Tag.of("key3", "value3"));
            tags.add(Tag.of("key4", "value4"));
            tags.add(Tag.of("key5", "value5"));
            return Tags.of(tags);
        }

        @Benchmark
        public Tags ofEmptyCollection() {
            return Tags.of(Collections.emptyList());
        }

        @Benchmark
        public Tags ofEmptyTags() {
            return Tags.of(Tags.empty());
        }

        public static void main(String[] args) throws RunnerException {
            Options opt = new OptionsBuilder().include(TagsOfBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();
            new Runner(opt).run();
        }

    }

    @Fork(1)
    @Measurement(iterations = 3)
    @Warmup(iterations = 5)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public static class TagsAndBenchmark {

        @Benchmark
        public Tags andVarargsString() { // allocating more; 360 B/op vs 336 on 1.13.14
            return Tags.of("key", "value").and("key2", "value2", "key3", "value3", "key4", "value4", "key5", "value5");
        }

        @Benchmark
        public Tags andTagVarargs() {
            return Tags.of("key", "value")
                .and(Tag.of("key2", "value2"), Tag.of("key3", "value3"), Tag.of("key4", "value4"),
                        Tag.of("key5", "value5"));
        }

        @Benchmark
        public Tags andTags() { // allocating more; 360 B/op vs 336 on 1.13.14
            return Tags.of("key", "value")
                .and(Tags.of("key2", "value2", "key3", "value3", "key4", "value4", "key5", "value5"));
        }

        @Benchmark
        public Tags andArrayList() {
            List<Tag> tags = new ArrayList<>(4);
            tags.add(Tag.of("key2", "value2"));
            tags.add(Tag.of("key3", "value3"));
            tags.add(Tag.of("key4", "value4"));
            tags.add(Tag.of("key5", "value5"));
            return Tags.of("key", "value").and(tags);
        }

        @Benchmark
        public Tags andEmptyCollection() {
            return Tags.of("key", "value").and(Collections.emptyList());
        }

        @Benchmark
        public Tags andEmptyTags() {
            return Tags.of("key", "value").and(Tags.empty());
        }

        public static void main(String[] args) throws RunnerException {
            Options opt = new OptionsBuilder().include(TagsAndBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();
            new Runner(opt).run();
        }

    }

}
