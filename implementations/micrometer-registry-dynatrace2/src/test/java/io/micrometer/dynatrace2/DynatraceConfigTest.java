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
package io.micrometer.dynatrace2;

import io.micrometer.core.instrument.config.validate.Validated;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.MAX_METRIC_LINES_PER_REQUEST;

class DynatraceConfigTest implements WithAssertions {
    private final Map<String, String> props = new HashMap<>();
    private final DynatraceConfig config = props::get;

    @Test
    void shouldBeValid_whenAllRequiredPropsAreSet() {
        props.put("dynatrace2.apiToken", "secret");
        props.put("dynatrace2.uri", "https://uri.dynatrace.com");
        props.put("dynatrace2.deviceName", "test-device");

        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void shouldBeInvalid_whenAllRequiredPropsAreMissing() {
        List<Validated.Invalid<?>> failures = config.validate().failures();

        assertThat(failures)
                .extracting(Validated.Invalid::getMessage)
                .containsOnly("is required");
    }

    @Test
    void shouldBeInvalid_whenBatchSizeIsBiggerThanMaxMetricLinesLimit() {
        props.put("dynatrace2.batchSize", String.valueOf(MAX_METRIC_LINES_PER_REQUEST + 1));

        List<Validated.Invalid<?>> failures = config.validate().failures();

        assertThat(failures)
                .extracting(Validated.Invalid::getProperty)
                .containsOnlyOnce("dynatrace2.batchSize");
    }

    @Test
    void shouldBeEmptyString_whenOptionalPropsAreNotSet() {
        props.put("dynatrace2.apiToken", "secret");
        props.put("dynatrace2.uri", "https://uri.dynatrace.com");

        assertThat(config.deviceName()).matches("");
        assertThat(config.entityId()).matches("");
        assertThat(config.groupName()).matches("");
    }
}
