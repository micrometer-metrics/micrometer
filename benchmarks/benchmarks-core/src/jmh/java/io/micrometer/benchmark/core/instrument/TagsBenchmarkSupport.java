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

import java.util.*;
import java.util.stream.Stream;

public class TagsBenchmarkSupport {

    public static final Random RANDOM = new Random(0x5EAL);

    public static final int DEFAULT_MAXIMUM_TAG_COUNT = 64;

    // 68 tags makes us able to select many different 64-element combinations
    // Given that there are only 68 elements these tags should never leave L2
    // It doesn't make much sense to create randomized Keys \times Values
    // variants, as we're interested in tag combinations rather than individual
    // tags, and also reusing the same keys would only result in them
    // overwriting each other.
    private static final Tag[] SAMPLES = {
            // 1 - 8
            Tag.of("challenge", "trivial"), Tag.of("status", "4xx"), Tag.of("error", "severe"),
            Tag.of("exception", "recoverable"),

            Tag.of("failure", "vm"), Tag.of("latency", "intolerable"), Tag.of("duration", "long"),
            Tag.of("role", "admin"),

            // 9 - 16
            Tag.of("song", "astonishing"), Tag.of("datacenter", "moon-east-1"), Tag.of("traffic", "moderate"),
            Tag.of("prices", "rising"),

            Tag.of("cores", "32"), Tag.of("ram", "64g"), Tag.of("disk", "nvme"), Tag.of("benchmark", "boring"),

            // 17 - 24
            Tag.of("kitchen", "clean"), Tag.of("cutlery", "new"), Tag.of("salmon", "fresh"),
            Tag.of("burger", "cheapskate"),

            Tag.of("access", "restricted"), Tag.of("entrance", "cellar"), Tag.of("shoes", "slippers"),
            Tag.of("ninja", "perfect"),

            // 25 - 32
            Tag.of("fallacy", "logical"), Tag.of("philosopher", "aristotel"), Tag.of("dining", "true"),
            Tag.of("demonstrates", "contention"),

            Tag.of("brand", "brandmann"), Tag.of("delivers", "groceries"), Tag.of("recognition", "nationwide"),
            Tag.of("balance", "positive"),

            // 33 - 40
            Tag.of("figure", "circle"), Tag.of("corners", "zero"), Tag.of("dimensions", "two"),
            Tag.of("domain", "geometry"),

            Tag.of("quality", "720p"), Tag.of("streams", "three"), Tag.of("captions", "english"),
            Tag.of("kbps", "1411"),

            // 41 - 48
            Tag.of("genre", "comedy"), Tag.of("format", "play"), Tag.of("location", "garden"),
            Tag.of("characters", "mixed"),

            Tag.of("delay", "hour"), Tag.of("transport", "railway"), Tag.of("compensation", "25%"),
            Tag.of("repetition", "daily"),

            // 49 - 56
            Tag.of("magnification", "binoculars"), Tag.of("signal", "analog"), Tag.of("gamma", "wowRGB"),
            Tag.of("light", "sun"),

            Tag.of("furniture", "table"), Tag.of("standing", "true"), Tag.of("sitting", "true"), Tag.of("engines", "2"),

            // 57 - 64
            Tag.of("computer", "desktop"), Tag.of("cables", "173"), Tag.of("cpu", "2024"), Tag.of("gpu", "2021"),

            Tag.of("warehouse", "narnia"), Tag.of("customer", "lion"), Tag.of("subscription", "platinum"),
            Tag.of("interest", "magic"),

            // 65 - 68
            Tag.of("form", "2477"), Tag.of("periodicity", "yearly"), Tag.of("submission", "initial"),
            Tag.of("history", "positive") };

    public static final int COUNT = SAMPLES.length;

    /**
     * @return A random tag from the prepared set.
     */
    public static Tag sample() {
        return SAMPLES[RANDOM.nextInt(SAMPLES.length)];
    }

    /**
     * @return The preconfigured set of tags. This is a finite stream (emitting every
     * preconfigured tag once) with a deterministic order.
     */
    public static Stream<Tag> samples() {
        return Arrays.stream(SAMPLES);
    }

    /**
     * @param mask Selection mask where every bit corresponds to a preconfigured tag.
     * @return A container with tags selected by the bitmask.
     */
    public static Tags selection(BitSet mask) {
        List<Tag> tags = new ArrayList<>();

        for (int i = 0; i < SAMPLES.length; i++) {
            if (mask.get(i)) {
                tags.add(SAMPLES[i]);
            }
        }

        return Tags.of(tags);
    }

    /**
     * @return A Tags object with arbitrary number of randomly selected tags.
     */
    public static Tags container() {
        return selection(BenchmarkSupport.selection(SAMPLES.length));
    }

    /**
     * @return A Tags object with exactly [count] number of randomly selected tags.
     */
    public static Tags container(int count) {
        return selection(BenchmarkSupport.selection(count, SAMPLES.length));
    }

    /**
     * @return A Tags object with a number of randomly selected tags between [minimum] and
     * [maximum].
     */
    public static Tags container(int minimum, int maximum) {
        return selection(BenchmarkSupport.selection(minimum, maximum, SAMPLES.length));
    }

    /**
     * @return A Tags object with a number of randomly selected tags between [minimum] and
     * [maximum]. Probability of this number being [mode] is equal to [probability], all
     * other variants are selected uniformly.
     */
    public static Tags biased(int minimum, int maximum, int mode, double probability) {
        return container(BenchmarkSupport.ModeUniformDistribution.sample(minimum, maximum, mode, probability));
    }

    /**
     * @return An infinite stream of Tags objects with arbitrary number of randomly
     * selected tags.
     */
    public static Stream<Tags> containers() {
        return Stream.generate(TagsBenchmarkSupport::container);
    }

    /**
     * @return An infinite stream of Tags objects with exactly [count] number of randomly
     * selected tags.
     */
    public static Stream<Tags> containers(int count) {
        return Stream.generate(() -> container(count));
    }

    /**
     * @return An infinite stream of Tags objects with a number of randomly selected tags
     * between [minimum] and [maximum].
     */
    public static Stream<Tags> containers(int minimum, int maximum) {
        return Stream.generate(() -> container(minimum, maximum));
    }

    /**
     * @return An infinite stream of Tags objects with a number of randomly selected tags
     * between [minimum] and [maximum]. Probability of this number being [mode] is equal
     * to [probability], all other variants are selected uniformly.
     */
    public static Stream<Tags> distribution(int minimum, int maximum, int mode, double probability) {
        return Stream.generate(() -> biased(minimum, maximum, mode, probability));
    }

    private TagsBenchmarkSupport() {
    }

}
