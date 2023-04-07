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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SignalFxNamingConvention}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class SignalFxNamingConventionTest {

    private SignalFxNamingConvention convention = new SignalFxNamingConvention();

    @Test
    void tagKey() {
        assertThat(convention.tagKey("_boo")).isEqualTo("a_boo");
        assertThat(convention.tagKey("__boo")).isEqualTo("a__boo");
        assertThat(convention.tagKey("sf_boo")).isEqualTo("asf_boo");
        assertThat(convention.tagKey("sf__boo")).isEqualTo("asf__boo");
        assertThat(convention.tagKey("sf_sf_boo")).isEqualTo("asf_sf_boo");

        assertThat(convention.tagKey("123")).isEqualTo("a123");
    }

    @Test
    void tagKeyWhenKeyHasDenylistedCharShouldSanitize() {
        assertThat(convention.tagKey("a.b")).isEqualTo("a_b");
    }

}
