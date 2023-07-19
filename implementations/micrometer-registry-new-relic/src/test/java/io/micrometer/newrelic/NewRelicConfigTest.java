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
package io.micrometer.newrelic;

import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewRelicConfigTest {

    private final Map<String, String> props = new HashMap<>();

    private final NewRelicConfig config = props::get;

    @Test
    void invalidClientProviderType() {
        props.put("newrelic.clientProviderType", "bad");

        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage))
            .containsExactly("should be one of 'INSIGHTS_API', 'INSIGHTS_AGENT'");
    }

    @Test
    void requiredConfigForInsightsApi() {
        props.put("newrelic.clientProviderType", "insights_api");

        assertThat(config.validateForInsightsApi().failures().stream().map(Validated.Invalid::getProperty))
            .containsExactlyInAnyOrder("newrelic.apiKey", "newrelic.accountId");
    }

    @Test
    void secretsNotRequiredForAgent() {
        props.put("newrelic.clientProviderType", "insights_agent");

        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void valid() {
        props.put("newrelic.accountId", "secret");
        props.put("newrelic.apiKey", "secret");

        assertThat(config.validateForInsightsApi().isValid()).isTrue();
    }

}
