/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.newrelic;

import io.micrometer.core.lang.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * Enum representing the possible NewRelic integration methods.
 *
 * @author Galen Schmidt
 */
public enum NewRelicIntegration {

    /**
     * API integration, in which the {@link NewRelicMeterRegistry}
     * will directly call the NewRelic REST API to publish metrics.
     */
    API,

    /**
     * APM integration, in which the {@link NewRelicMeterRegistry}
     * will use {@link com.newrelic.api.agent.Insights#recordCustomEvent(String, Map)} to publish metrics.
     */
    APM;

    /**
     * Returns the {@code NewRelicIntegration} for the given name (case insensitive),
     * or {@link Optional#empty()} if none match.
     *
     * @param input The enum name, as returned by {@link #name()}
     * @return The enum value with the given name, if one exists
     */
    public static Optional<NewRelicIntegration> fromString(@Nullable String input) {

        for (NewRelicIntegration value : NewRelicIntegration.values()) {
            if (value.name().equalsIgnoreCase(input)) {
                return Optional.of(value);
            }
        }

        return Optional.empty();
    }

}
