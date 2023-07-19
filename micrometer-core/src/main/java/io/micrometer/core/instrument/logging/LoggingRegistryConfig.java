/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.core.instrument.logging;

import io.micrometer.core.instrument.step.StepRegistryConfig;

/**
 * Configuration for {@link LoggingMeterRegistry}.
 *
 * @author Jon Schneider
 * @since 1.1.0
 */
public interface LoggingRegistryConfig extends StepRegistryConfig {

    LoggingRegistryConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "logging";
    }

    /**
     * @return Whether counters and timers that have no activity in an interval are still
     * logged.
     */
    default boolean logInactive() {
        String v = get(prefix() + ".logInactive");
        return Boolean.parseBoolean(v);
    }

}
