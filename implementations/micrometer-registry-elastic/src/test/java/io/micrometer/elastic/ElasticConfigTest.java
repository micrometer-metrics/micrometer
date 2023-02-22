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
package io.micrometer.elastic;

import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticConfigTest {

    private final Map<String, String> props = new HashMap<>();

    private final ElasticConfig config = props::get;

    @Test
    void invalid() {
        props.put("elastic.host", "invalid");
        props.put("elastic.indexDateFormat", "invalid");

        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage))
            .containsExactlyInAnyOrder("must be a valid URL", "invalid date format");
    }

    @Test
    void invalidOverrideDateFormat() {
        Validated<?> validate = new ElasticConfig() {
            @Override
            public String indexDateFormat() {
                return "invalid";
            }

            @Override
            public String get(String key) {
                return null;
            }
        }.validate();

        assertThat(validate.failures().stream().map(Validated.Invalid::getMessage))
            .containsExactly("invalid date format");
    }

    @Test
    void valid() {
        assertThat(config.validate().isValid()).isTrue();
    }

}
