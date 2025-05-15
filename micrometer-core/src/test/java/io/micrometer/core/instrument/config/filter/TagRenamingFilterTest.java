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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TagRenamingFilterTest {

    private static Meter.Id identifier(String name, String... tags) {
        return new Meter.Id(name, Tags.of(tags), null, null, Meter.Type.COUNTER);
    }

    static Stream<Arguments> variations() {
        return Stream.of(Arguments.of(identifier("meter"), "mismatch", "source", "target", Tags.empty()),
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v"), "mismatch", "source",
                        "target", Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                // Case sensitivity
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v"), "METER", "source",
                        "target", Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                // No such tag
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v"), "meter", "source",
                        "target", Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                // No tag with key in such case
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v"), "meter", "ALFA", "target",
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                // Nope, no substring search in key either
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v"), "meter", "lf", "target",
                        Tags.of("alfa", "v", "bravo", "v", "charlie", "v")),
                // Oh finally
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v", "delta", "v"), "meter",
                        "alfa", "renamed", Tags.of("renamed", "v", "bravo", "v", "charlie", "v", "delta", "v")),
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v", "delta", "v"), "meter",
                        "bravo", "renamed", Tags.of("alfa", "v", "renamed", "v", "charlie", "v", "delta", "v")),
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v", "delta", "v"), "meter",
                        "charlie", "renamed", Tags.of("alfa", "v", "bravo", "v", "renamed", "v", "delta", "v")),
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v", "delta", "v"), "meter",
                        "delta", "renamed", Tags.of("alfa", "v", "bravo", "v", "charlie", "v", "renamed", "v")),

                // Same, but with a prefix instead of full meter name match
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v", "delta", "v"), "m", "alfa",
                        "renamed", Tags.of("renamed", "v", "bravo", "v", "charlie", "v", "delta", "v")),
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v", "delta", "v"), "m", "bravo",
                        "renamed", Tags.of("alfa", "v", "renamed", "v", "charlie", "v", "delta", "v")),
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v", "delta", "v"), "m",
                        "charlie", "renamed", Tags.of("alfa", "v", "bravo", "v", "renamed", "v", "delta", "v")),
                Arguments.of(identifier("meter", "alfa", "v", "bravo", "v", "charlie", "v", "delta", "v"), "m", "delta",
                        "renamed", Tags.of("alfa", "v", "bravo", "v", "charlie", "v", "renamed", "v")));
    }

    @ParameterizedTest
    @MethodSource("variations")
    void variation(Meter.Id identifier, String prefix, String key, String replacement, Tags expectation) {
        MeterFilter sut = TagRenamingFilter.of(prefix, key, replacement);

        assertThat(sut.map(identifier).getTagsAsIterable()).isEqualTo(expectation);
    }

}
