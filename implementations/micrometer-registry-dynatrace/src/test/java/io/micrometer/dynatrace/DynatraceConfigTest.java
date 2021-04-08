/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynatraceConfigTest {
    private final Map<String, String> props = new HashMap<>();
    private final DynatraceConfig config = props::get;

    @Test
    void invalid() {
        List<Validated.Invalid<?>> failures = config.validate().failures();
        assertThat(failures.stream().map(Validated.Invalid::getMessage))
                .containsOnly("is required");
        assertThat(failures.size()).isEqualTo(3);
    }

    @Test
    void invalidOverrideTechnologyType() {
        Validated<?> validate = new DynatraceConfig() {
            @Override
            public String technologyType() {
                return "";
            }

            @Override
            public String get(String key) {
                return null;
            }
        }.validate();

        assertThat(validate.failures().stream().map(Validated.Invalid::getMessage))
                .contains("cannot be blank");
    }

    @Test
    void valid() {
        props.put("dynatrace.apiToken", "secret");
        props.put("dynatrace.uri", "https://uri.dynatrace.com");
        props.put("dynatrace.deviceId", "device");

        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void invalidVersion() {
        Map<String, String> properties = new HashMap<String, String>() {{
            put("dynatrace.apiToken", "secret");
            put("dynatrace.uri", "https://uri.dynatrace.com");
            put("dynatrace.deviceId", "device");
            put("dynatrace.apiVersion", "v-INVALID");
        }};

        DynatraceConfig config = properties::get;

        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void testFallbackToV1() {
        Map<String, String> properties = new HashMap<String, String>() {{
            put("dynatrace.apiToken", "secret");
            put("dynatrace.uri", "https://uri.dynatrace.com");
            put("dynatrace.deviceId", "device");
        }};

        DynatraceConfig config = properties::get;
        assertThat(config.apiVersion()).isEqualTo("v1");
        Validated<?> validated = config.validate();
        assertThat(validated.isValid()).isTrue();
    }
}
