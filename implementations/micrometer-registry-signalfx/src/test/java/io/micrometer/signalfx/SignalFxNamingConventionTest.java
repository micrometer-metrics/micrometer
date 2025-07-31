/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.signalfx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SignalFxNamingConvention}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
@SuppressWarnings("deprecation")
class SignalFxNamingConventionTest {

    private final SignalFxNamingConvention convention = new SignalFxNamingConvention();

    @ParameterizedTest
    @ValueSource(strings = { "_boo", "__boo", "sf_boo", "sf__boo", "sf_sf_boo", "sf.boo", "àboo", "sf_àboo", "boo" })
    void tagKeyShouldStartWithAlphabet(String key) {
        assertThat(convention.tagKey(key)).isEqualTo("boo");
    }

    @ParameterizedTest
    @ValueSource(strings = { "123", "_123", "sf_123", "sf.123", "_.123" })
    void tagKeyShouldBePrefixed(String key) {
        assertThat(convention.tagKey(key)).isEqualTo("a123");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "_", ".", "à", "sf_", "sf_.", "sf_à" })
    void tagKeyShouldBeEmpty(String key) {
        assertThat(convention.tagKey(key)).isEmpty();
    }

    @Test
    void tagKeyWhenKeyHasDenylistedCharShouldSanitize() {
        assertThat(convention.tagKey("a.b")).isEqualTo("a_b");
        assertThat(convention.tagKey("booàboo")).isEqualTo("boo_boo");
    }

}
