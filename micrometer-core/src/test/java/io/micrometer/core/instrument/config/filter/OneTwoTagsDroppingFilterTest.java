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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OneTwoTagsDroppingFilterTest {

    static Stream<Arguments> variations() {
        return DroppingFilterTestSupport.variations().filter(candidate -> {
            int keys = ((String[]) candidate.get()[1]).length;
            return keys == 1 || keys == 2;
        });
    }

    private static MeterFilter create(String... keys) {
        switch (keys.length) {
            case 1:
                return OneTwoTagsDroppingFilter.of(keys[0]);
            case 2:
                return OneTwoTagsDroppingFilter.of(keys[0], keys[1]);
            default:
                throw new IllegalArgumentException(
                        "Invalid amount of keys specified (1 or 2 expected): " + Arrays.toString(keys));
        }
    }

    @ParameterizedTest
    @MethodSource("variations")
    void variation(Tag[] existing, String[] keys, Tag[] expectation) {
        Meter.Id identifier = new Meter.Id("any", Tags.of(existing), null, null, Meter.Type.COUNTER);
        MeterFilter sut = create(keys);

        assertThat(sut.map(identifier).getTags()).containsExactly(expectation);
    }

}
