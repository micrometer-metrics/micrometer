/**
 * Copyright 2018 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.autoconfigure.export.logging;

import io.micrometer.core.instrument.logging.LoggingRegistryConfig;
import io.micrometer.spring.autoconfigure.export.properties.StepRegistryPropertiesConfigAdapter;

/**
 * Adapter to convert {@link LoggingRegistryProperties} to an {@link LoggingRegistryConfig}.
 *
 * @author Jon Schneider
 */
class LoggingRegistryPropertiesConfigAdapter extends StepRegistryPropertiesConfigAdapter<LoggingRegistryProperties> implements LoggingRegistryConfig {

    LoggingRegistryPropertiesConfigAdapter(LoggingRegistryProperties properties) {
        super(properties);
    }

    @Override
    public boolean logInactive() {
        return get(LoggingRegistryProperties::isLogInactive, LoggingRegistryConfig.super::logInactive);
    }
}
