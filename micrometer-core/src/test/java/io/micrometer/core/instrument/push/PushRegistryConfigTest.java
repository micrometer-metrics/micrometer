/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.push;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.config.validate.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushRegistryConfigTest {

    private final Map<String, String> props = new HashMap<>();

    private final PushRegistryConfig config = new PushRegistryConfig() {
        @Override
        public String prefix() {
            return "push";
        }

        @Override
        public String get(String key) {
            return props.get(key);
        }
    };

    @Test
    void invalid() {
        props.put("push.enabled", "nope"); // anything but "true" results in false but is
                                           // still valid
        props.put("push.numThreads", "1.1");
        props.put("push.connectTimeout", "1w");
        props.put("push.readTimeout", "1w");
        props.put("push.batchSize", "Z");
        props.put("push.step", "up");

        // overall not valid
        assertThat(config.validate().isValid()).isFalse();

        // can iterate over failures to display messages
        List<Validated.Invalid<?>> failures = config.validate().failures();

        assertThat(failures.size()).isEqualTo(5);
        assertThat(failures.stream().map(Validated.Invalid::getMessage)).containsOnly("must be a valid duration",
                "must contain a valid time unit", "must be an integer");

        assertThatThrownBy(config::batchSize).isInstanceOf(ValidationException.class);
    }

    @Test
    void valid() {
        props.put("push.numThreads", "1");
        props.put("push.connectTimeout", "1s");
        props.put("push.readTimeout", "1s");
        props.put("push.batchSize", "3");
        props.put("push.step", "1s");

        assertThat(config.validate().isValid()).isTrue();
    }

}
