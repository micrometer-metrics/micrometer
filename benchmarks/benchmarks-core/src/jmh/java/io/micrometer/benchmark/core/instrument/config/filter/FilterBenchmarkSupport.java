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
package io.micrometer.benchmark.core.instrument.config.filter;

import io.micrometer.benchmark.core.instrument.TagsBenchmarkSupport;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;

import java.util.stream.Stream;

public class FilterBenchmarkSupport {

    // A generic upper bound on the number of tags in an identifier
    public static final double DEFAULT_MODE_PROBABILITY = 0.9;

    private FilterBenchmarkSupport() {
    }

    public static Meter.Id toMeter(Tags tags) {
        return new Meter.Id("_irrelevant_", tags, null, null, Meter.Type.COUNTER);
    }

    /**
     * @return An identifier having between [minimum] and [maximum] tags. With
     * [probability] it will have exactly [mode] number of tags, other options are
     * selected evenly.
     */
    public static Meter.Id identifier(int minimum, int maximum, int mode, double probability) {
        return toMeter(TagsBenchmarkSupport.biased(minimum, maximum, mode, probability));
    }

    /**
     * @return An infinite stream of identifiers having between [minimum] and [maximum]
     * tags. With [probability] they will have exactly [mode] number of tags, other
     * options are selected evenly.
     */
    public static Stream<Meter.Id> distributed(int minimum, int maximum, int mode, double probability) {
        return Stream.generate(() -> identifier(minimum, maximum, mode, probability));
    }

    /**
     * @return An infinite stream of identifiers having between arbitrary number of tags.
     * With [probability] they will have exactly [mode] number of tags, other options are
     * selected evenly.
     */
    public static Stream<Meter.Id> distributed(int mode, double probability) {
        return distributed(0, TagsBenchmarkSupport.DEFAULT_MAXIMUM_TAG_COUNT, mode, probability);
    }

    /**
     * @return An infinite stream of identifiers having between arbitrary number of tags.
     * With {@link #DEFAULT_MODE_PROBABILITY} they will have exactly [mode] number of
     * tags, other options are selected evenly.
     */
    public static Stream<Meter.Id> distributed(int mode) {
        return distributed(mode, DEFAULT_MODE_PROBABILITY);
    }

    /**
     * @return Infinite stream of identifiers that have between [minimum] and [maximum]
     * (inclusive) tags, with equal probability for every possible number.
     */
    public static Stream<Meter.Id> identifiers(int minimum, int maximum) {
        return TagsBenchmarkSupport.containers(minimum, maximum).map(FilterBenchmarkSupport::toMeter);
    }

    /**
     * @return Infinite stream of identifiers that have exactly [count] tags.
     */
    public static Stream<Meter.Id> identifiers(int count) {
        return identifiers(count, count);
    }

}
