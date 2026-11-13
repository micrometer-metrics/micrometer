/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.datadog;

import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatadogConfigTest {

    private final Map<String, String> props = new HashMap<>();

    private final DatadogConfig config = props::get;

    @Test
    void invalid() {
        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage))
            .containsExactly("is required");
    }

    @Test
    void valid() {
        props.put("datadog.apiKey", "secret");
        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void defaultsHostTagWhenMissing() {
        assertThat(config.hostTag()).isEqualTo("instance");
    }

    @Test
    void defaultsCompressToTrue() {
        assertThat(config.compress()).isTrue();
    }

    @Test
    void defaultsApiVersionToV1() {
        assertThat(config.apiVersion()).isEqualTo(DatadogApiVersion.V1);
    }

    @Test
    void apiVersionCanBeSetToV2() {
        props.put("datadog.apiVersion", "v2");

        assertThat(config.apiVersion()).isEqualTo(DatadogApiVersion.V2);
    }

    @Test
    void invalidApiVersion() {
        props.put("datadog.apiKey", "secret");
        props.put("datadog.apiVersion", "v3");

        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage))
            .containsExactly("should be one of 'V1', 'V2'");
    }

    @Test
    void compressCanBeDisabled() {
        props.put("datadog.compress", "false");

        assertThat(config.compress()).isFalse();
    }

}
