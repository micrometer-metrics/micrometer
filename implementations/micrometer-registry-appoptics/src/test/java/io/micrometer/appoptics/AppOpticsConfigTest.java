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
package io.micrometer.appoptics;

import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppOpticsConfigTest {

    private final Map<String, String> props = new HashMap<>();

    private final AppOpticsConfig config = props::get;

    @Test
    void invalid() {
        props.put("appoptics.uri", "bad");
        props.put("appoptics.batchSize", Integer.toString(AppOpticsConfig.MAX_BATCH_SIZE * 2));

        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage))
            .containsExactlyInAnyOrder("must be a valid URL", "is required");
    }

    @Test
    void valid() {
        props.put("appoptics.apiToken", "secret");

        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void invalidOverrideBatchSize() {
        Validated<?> validate = new AppOpticsConfig() {
            @Override
            public int batchSize() {
                return AppOpticsConfig.MAX_BATCH_SIZE * 2;
            }

            @Override
            public String apiToken() {
                return "secret";
            }

            @Override
            public String get(String key) {
                return null;
            }
        }.validate();

        assertThat(validate.failures().stream().map(Validated.Invalid::getMessage))
            .containsOnly("cannot be greater than " + AppOpticsConfig.MAX_BATCH_SIZE);
    }

}
