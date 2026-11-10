/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ValidatedKeyValue}.
 *
 * @author zbnerd
 */
class ValidatedKeyValueTest {

    @Test
    @SuppressWarnings("NullAway")
    void constructorWhenKeyIsNullThrowsNullPointerException() {
        assertThatThrownBy(() -> new ValidatedKeyValue<>(null, "value", value -> true))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void factoryWhenKeyIsNullThrowsNullPointerException() {
        assertThatThrownBy(() -> KeyValue.of((String) null, "value", value -> true))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void factoryCreatesValidatedKeyValue() {
        KeyValue keyValue = KeyValue.of("key", "value", value -> true);
        assertThat(keyValue.getKey()).isEqualTo("key");
        assertThat(keyValue.getValue()).isEqualTo("value");
    }

}
