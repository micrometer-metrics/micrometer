/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.config;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.config.validate.ValidationException;

public interface MeterRegistryConfig {

    String prefix();

    /**
     * Get the value associated with a key.
     * @param key Key to lookup in the config.
     * @return Value for the key or null if no key is present.
     */
    @Nullable
    String get(String key);

    /**
     * Validate configuration.
     * @return validation result
     * @since 1.5.0
     */
    default Validated<?> validate() {
        return Validated.none();
    }

    /**
     * Validate configuration and throw {@link ValidationException} if it's not valid.
     * @throws ValidationException if it's not valid
     * @since 1.5.0
     */
    default void requireValid() throws ValidationException {
        validate().orThrow();
    }

}
