/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.core.instrument.config.validate;

import io.micrometer.core.instrument.config.MeterRegistryConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyValidatorTest {

    Properties props = new Properties();

    MeterRegistryConfig config = new MeterRegistryConfig() {
        @Override
        public String prefix() {
            return "test";
        }

        @Override
        public String get(String key) {
            return props.getProperty(key);
        }
    };

    @Test
    void stringMapValid() {
        props.put("test.map", "a=1,b=2");
        Validated<Map<String, Integer>> validated = PropertyValidator.getStringMap(config, "map", Integer::parseInt);
        assertThat(validated.isValid()).isTrue();
    }

    @Test
    void stringMapIsValidButWeird() {
        props.put("test.map", "a= b ,c =d, e = f");
        Validated<Map<String, String>> validated = PropertyValidator.getStringMap(config, "map", Function.identity());
        assertThat(validated.isValid()).isTrue();
        assertThat(validated.get()).containsExactlyInAnyOrderEntriesOf(Map.of("a", "b", "c", "d", "e", "f"));
    }

    @Test
    void stringMapBlank() {
        props.put("test.map", " ");
        Validated<Map<String, Integer>> validated = PropertyValidator.getStringMap(config, "map", Integer::parseInt);
        assertThat(validated.isValid()).isTrue();
        assertThat(validated.get()).isNull();
        assertThat(validated.orElse(Collections.emptyMap())).isEmpty();
    }

    @Test
    void stringMapInvalid() {
        props.setProperty("test.map", "a=1,b=c");
        Validated<Map<String, Integer>> validated = PropertyValidator.getStringMap(config, "map", Integer::parseInt);
        assertThat(validated.isInvalid()).isTrue();
    }

}
