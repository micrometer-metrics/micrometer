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
    void validApiKey() {
        props.put("datadog.apiKey", "secret");
        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void validNamedPipe() {
        props.put("datadog.uri", "unix:///var/run/datadog.sock");
        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void validStatsdTCP() {
        props.put("datadog.uri", "tcp://localhost:8125");
        assertThat(config.validate().isValid()).isTrue();
        props.clear();
        props.put("datadog.uri", "tcp://localhost");
        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void validStatsdUDP() {
        props.put("datadog.uri", "udp://localhost");
        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void validStatsdDiscovery() {
        props.put("datadog.uri", "discovery:///");
        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void defaultsHostTagWhenMissing() {
        assertThat(config.hostTag()).isEqualTo("instance");
    }

}
