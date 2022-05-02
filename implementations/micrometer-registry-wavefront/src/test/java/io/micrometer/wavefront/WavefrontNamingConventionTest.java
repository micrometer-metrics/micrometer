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
package io.micrometer.wavefront;

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WavefrontNamingConventionTest {

    private final WavefrontNamingConvention convention = new WavefrontNamingConvention(null);

    @Test
    void name() {
        assertThat(convention.name("123abc/{:id}水", Meter.Type.GAUGE)).isEqualTo("123abc/__id__");
    }

    @Test
    void tagKey() {
        assertThat(convention.tagKey("123abc/{:id}水")).isEqualTo("123abc___id__");
    }

    @Test
    void tagValue() {
        assertThat(convention.tagValue("123abc/\"{:id}水\\")).isEqualTo("123abc/\\\"{:id}水_");
        assertThat(convention.tagValue("\\")).isEqualTo("_");
    }

}
