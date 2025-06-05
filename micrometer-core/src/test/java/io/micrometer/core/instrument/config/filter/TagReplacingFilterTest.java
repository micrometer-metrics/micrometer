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
package io.micrometer.core.instrument.config.filter;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagReplacingFilterTest {

    private static final String REPLACEMENT = "replacement";

    static Stream<Arguments> classicSamples() {
        return Stream.of(
                // Sanity check
                Arguments.of(Tags.empty(), "missing", new String[0], Tags.empty()),

                // Absence
                Arguments.of(Tags.of("alfa", "v"), "missing", new String[0], Tags.of("alfa", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v"), "missing", new String[0],
                        Tags.of("alfa", "v", "bravo", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "missing", new String[0],
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),

                // Case sensitivity
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "", new String[0],
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "Bravo", new String[0],
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "Charlie", new String[0],
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),

                // Normal replacement
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "alfa", new String[0],
                        Tags.of("alfa", REPLACEMENT, "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "bravo", new String[0],
                        Tags.of("alfa", "v", "bravo", REPLACEMENT, "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "charlie", new String[0],
                        Tags.of("alfa", "v", "bravo", "v", "charlie", REPLACEMENT)),

                // Exceptions blockout
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "alfa", new String[] { "v" },
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "bravo", new String[] { "v" },
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "charlie", new String[] { "v" },
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),

                // Nothing happens if exceptions don't match anything
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "alfa", new String[] { "miss" },
                        Tags.of("alfa", REPLACEMENT, "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "bravo", new String[] { "miss" },
                        Tags.of("alfa", "v", "bravo", REPLACEMENT, "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "charlie", new String[] { "miss" },
                        Tags.of("alfa", "v", "bravo", "v", "charlie", REPLACEMENT)),

                // Normal behavior returns if just one of them works
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "alfa", new String[] { "v", "miss" },
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "bravo", new String[] { "v", "miss" },
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), "charlie",
                        new String[] { "v", "miss" }, Tags.of("alfa", "v", "bravo", "v", "charlie", "v")));
    }

    private static Set<Tag> lookup(Tag... tags) {
        return new HashSet<>(Arrays.asList(tags));
    }

    static Stream<Arguments> genericSamples() {
        return Stream.of(Arguments.of(Tags.empty(), new HashSet<>(), Tags.empty()),
                Arguments.of(Tags.of("alfa", "v"), new HashSet<>(), Tags.of("alfa", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v"), new HashSet<>(), Tags.of("alfa", "v", "bravo", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), new HashSet<>(),
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),

                // Filter mismatch
                Arguments.of(Tags.empty(), lookup(Tag.of("alfa", "mismatch")), Tags.empty()),
                Arguments.of(Tags.of("alfa", "v"), lookup(Tag.of("alfa", "mismatch")), Tags.of("alfa", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v"), lookup(Tag.of("alfa", "mismatch")),
                        Tags.of("alfa", "v", "bravo", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), lookup(Tag.of("alfa", "mismatch")),
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), lookup(Tag.of("alfa", "mismatch")),
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"), lookup(Tag.of("charlie", "mismatch")),
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),

                // Filter match
                Arguments.of(Tags.empty(),
                        lookup(Tag.of("alfa", "mismatch"), Tag.of("alfa", "v"), Tag.of("bravo", "mismatch")),
                        Tags.empty()),
                Arguments.of(Tags.of("alfa", "v"),
                        lookup(Tag.of("alfa", "mismatch"), Tag.of("alfa", "v"), Tag.of("bravo", "mismatch")),
                        Tags.of("alfa", "alfa")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v"),
                        lookup(Tag.of("alfa", "mismatch"), Tag.of("alfa", "v"), Tag.of("bravo", "mismatch")),
                        Tags.of("alfa", "alfa", "bravo", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"),
                        lookup(Tag.of("alfa", "mismatch"), Tag.of("alfa", "v"), Tag.of("bravo", "mismatch")),
                        Tags.of("alfa", "alfa", "bravo", "v", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"),
                        lookup(Tag.of("alfa", "mismatch"), Tag.of("bravo", "v"), Tag.of("bravo", "mismatch")),
                        Tags.of("alfa", "v", "bravo", "bravo", "charlie", "v")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"),
                        lookup(Tag.of("alfa", "mismatch"), Tag.of("charlie", "v"), Tag.of("bravo", "mismatch")),
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "charlie")),
                Arguments.of(Tags.of("alfa", "v", "bravo", "v", "charlie", "v"),
                        lookup(Tag.of("alfa", "mismatch"), Tag.of("alfa", "v"), Tag.of("bravo", "mismatch"),
                                Tag.of("bravo", "v"), Tag.of("charlie", "v")),
                        Tags.of("alfa", "alfa", "bravo", "bravo", "charlie", "charlie")));
    }

    @ParameterizedTest
    @MethodSource("classicSamples")
    void classic(Tags input, String key, String[] exceptions, Tags expectation) {
        MeterFilter sut = TagReplacingFilter.classicValueReplacing(key, any -> REPLACEMENT, exceptions);

        Meter.Id argument = new Meter.Id("_irrelevant_", input, null, null, Meter.Type.COUNTER);

        assertThat(sut.map(argument).getTagsAsIterable()).isEqualTo(expectation);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @MethodSource("genericSamples")
    void generic(Tags input, Set<Tag> matching, Tags expectation) {
        BiPredicate<String, String> matcher = mock(BiPredicate.class);
        when(matcher.test(any(), any())).then(arguments -> {
            String key = arguments.getArgument(0);
            String value = arguments.getArgument(1);
            return matching.contains(Tag.of(key, value));
        });

        BiFunction<String, String, Tag> replacer = mock(BiFunction.class);
        when(replacer.apply(any(), any())).then(arguments -> {
            String key = arguments.getArgument(0);
            return Tag.of(key, key);
        });

        MeterFilter sut = TagReplacingFilter.of(matcher, replacer);

        Meter.Id argument = new Meter.Id("_irrelevant_", input, null, null, Meter.Type.COUNTER);

        assertThat(sut.map(argument).getTagsAsIterable()).isEqualTo(expectation);

        for (Tag tag : input) {
            verify(matcher, times(1)).test(tag.getKey(), tag.getValue());

            int invocations = matching.contains(tag) ? 1 : 0;
            verify(replacer, times(invocations)).apply(tag.getKey(), tag.getValue());
        }
    }

}
