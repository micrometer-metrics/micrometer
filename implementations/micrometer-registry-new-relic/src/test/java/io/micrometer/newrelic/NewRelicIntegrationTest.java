/**
 * Copyright 2019 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.newrelic;


import io.micrometer.core.lang.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static io.micrometer.newrelic.NewRelicIntegration.API;
import static io.micrometer.newrelic.NewRelicIntegration.APM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Galen Schmidt
 */
class NewRelicIntegrationTest {

    private static Stream<Arguments> fromStringTestParameters() {
        return Stream.of(
                Arguments.of("API", API),
                Arguments.of("APM", APM),

                Arguments.of("api", API),
                Arguments.of("apm", APM),

                Arguments.of("Api", API),
                Arguments.of("Apm", APM),

                Arguments.of("INVALID", null),

                Arguments.of("", null),
                Arguments.of(null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("fromStringTestParameters")
    void fromStringTest(String input, @Nullable NewRelicIntegration expected) {
        Optional<NewRelicIntegration> maybeIntegration = NewRelicIntegration.fromString(input);

        if (expected == null) {
            assertThat(maybeIntegration).isEmpty();
        } else {
            assertThat(maybeIntegration).contains(expected);
        }

    }

}
