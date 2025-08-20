/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.benchmark.core.instrument;

import io.micrometer.benchmark.BenchmarkSupport;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TagsBenchmarks {

    private static final String NULL_GENERATOR_NAME = "null";

    private static final String TAGS_GENERATOR_NAME = "tags";

    private static final String LIST_GENERATOR_NAME = "list";

    private static final String SET_GENERATOR_NAME = "set";

    private static final String ITERABLE_GENERATOR_NAME = "iterable";

    private static final String[] GENERATOR_NAMES = { NULL_GENERATOR_NAME, TAGS_GENERATOR_NAME, LIST_GENERATOR_NAME,
            SET_GENERATOR_NAME, ITERABLE_GENERATOR_NAME };

    private static final String ABSENT_DOMINANT = "absent";

    private static final Random RANDOM = new Random(0x5EAL);

    private static final int COUNT = 1 << 10;

    private static final int MASK = COUNT - 1;

    private static final Supplier<Iterable<Tag>> NULL_GENERATOR = () -> null;

    private static final Supplier<Iterable<Tag>> TAGS_GENERATOR = TagsBenchmarkSupport::container;

    private static final Supplier<Iterable<Tag>> LIST_GENERATOR = () -> TagsBenchmarkSupport.container()
        .stream()
        .collect(Collectors.toList());

    private static final Supplier<Iterable<Tag>> SET_GENERATOR = () -> TagsBenchmarkSupport.container()
        .stream()
        .collect(Collectors.toSet());

    private static final Supplier<Iterable<Tag>> ITERABLE_GENERATOR = () -> TagsBenchmarkSupport.container()::iterator;

    private static final Map<String, Supplier<Iterable<Tag>>> GENERATORS = new HashMap<>();

    static {
        GENERATORS.put(NULL_GENERATOR_NAME, NULL_GENERATOR);
        GENERATORS.put(TAGS_GENERATOR_NAME, TAGS_GENERATOR);
        GENERATORS.put(LIST_GENERATOR_NAME, LIST_GENERATOR);
        GENERATORS.put(SET_GENERATOR_NAME, SET_GENERATOR);
        GENERATORS.put(ITERABLE_GENERATOR_NAME, ITERABLE_GENERATOR);
    }

    private static String category(String dominant) {
        if (Arrays.binarySearch(GENERATOR_NAMES, dominant) != -1 && RANDOM.nextDouble() < 0.9) {
            return dominant;
        }

        return GENERATOR_NAMES[RANDOM.nextInt(GENERATOR_NAMES.length)];
    }

    private TagsBenchmarks() {
    }

    /**
     * Evaluates performance of Tags.of() calls against different kinds of sources. Except
     * for the actual underlying type, all inputs contain a uniformly distributed number
     * of tags in [0..64] range. The [dominant] parameter defines the category that would
     * occupy 90% of the samples, other will be evenly distributed in the remaining 10%.
     */
    @Fork(value = 1)
    @Warmup(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 54, time = 10, timeUnit = TimeUnit.SECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Benchmark)
    public static class Of {

        @Param({ ABSENT_DOMINANT, NULL_GENERATOR_NAME, TAGS_GENERATOR_NAME, LIST_GENERATOR_NAME, SET_GENERATOR_NAME,
                ITERABLE_GENERATOR_NAME })
        public String dominant;

        private Iterable<Tag>[] samples;

        private int iteration;

        @SuppressWarnings("unchecked")
        @Setup
        public void setUp() {
            samples = Stream.generate(() -> category(dominant))
                .map(category -> GENERATORS.get(category).get())
                .limit(COUNT)
                .toArray(Iterable[]::new);
        }

        @Benchmark
        public Tags baseline() {
            return Tags.of(samples[iteration++ & MASK]);
        }

        public static void main(String[] args) throws RunnerException {
            BenchmarkSupport.run(Of.class);
        }

    }

    /**
     * Evaluates performance of Tags.concat() calls against different kinds of sources.
     * Except for the actual underlying type, all inputs contain a uniformly distributed
     * number of tags in [0..64] range. The [dominant] parameter defines the category that
     * would occupy 90% of the samples, other will be evenly distributed in the remaining
     * 10%.
     */
    @Fork(value = 1)
    @Warmup(iterations = 6, time = 10, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 12, time = 10, timeUnit = TimeUnit.SECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Benchmark)
    public static class Concat {

        @Param({ ABSENT_DOMINANT, NULL_GENERATOR_NAME, TAGS_GENERATOR_NAME, LIST_GENERATOR_NAME, SET_GENERATOR_NAME,
                ITERABLE_GENERATOR_NAME })
        public String dominantSource;

        @Param({ ABSENT_DOMINANT, NULL_GENERATOR_NAME, TAGS_GENERATOR_NAME, LIST_GENERATOR_NAME, SET_GENERATOR_NAME,
                ITERABLE_GENERATOR_NAME })
        public String dominantAddition;

        private Iterable<Tag>[] sources;

        private Iterable<Tag>[] additions;

        private int source;

        private int addition;

        @SuppressWarnings("unchecked")
        @Setup
        public void setUp() {
            sources = Stream.generate(() -> category(dominantSource))
                .map(category -> GENERATORS.get(category).get())
                .limit(COUNT)
                .toArray(Iterable[]::new);

            additions = Stream.generate(() -> category(dominantAddition))
                .map(category -> GENERATORS.get(category).get())
                .limit(COUNT)
                .toArray(Iterable[]::new);
        }

        @Benchmark
        public Tags baseline() {
            int index = addition += BenchmarkSupport.SAMPLE_STEP;

            return Tags.concat(sources[source++ & MASK], additions[index & MASK]);
        }

        public static void main(String[] args) throws RunnerException {
            BenchmarkSupport.run(Concat.class,
                    // Not the coolest thing to do, but we're dealing with 6x6 matrix
                    new OptionsBuilder().measurementIterations(12));
        }

    }

}
