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
package io.micrometer.ganglia;

import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GangliaConfigTest {

    private final Map<String, String> props = new HashMap<>();

    private final GangliaConfig config = props::get;

    @Test
    void invalid() {
        props.put("ganglia.ttl", "1.1");
        props.put("ganglia.addressingMode", "dne");
        props.put("ganglia.port", "what?");
        props.put("ganglia.durationUnits", "weeks");

        // overall not valid
        assertThat(config.validate().isValid()).isFalse();

        // can iterate over failures to display messages
        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage)).containsExactlyInAnyOrder(
                "must be an integer", "should be one of 'MULTICAST', 'UNICAST'", "must be an integer",
                "must contain a valid time unit");
    }

    @Test
    void valid() {
        props.put("ganglia.ttl", "1");
        props.put("ganglia.addressingMode", "MULTICAST");
        props.put("ganglia.port", "123");

        assertThat(config.validate().isValid()).isTrue();
    }

}
