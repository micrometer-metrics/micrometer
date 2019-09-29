/**
 * Copyright 2017 Pivotal Software, Inc.
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

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;

/**
 * Configuration for {@link NewRelicMeterRegistry}.
 *
 * @author Jon Schneider
 * @since 1.0.0
 */
public interface NewRelicConfig extends StepRegistryConfig {

    @Override
    default String prefix() {
        return "newrelic";
    }

    /**
     * When this is {@code false}, the New Relic eventType value will be set to {@link #eventType()}. Otherwise, the meter name will be used.
     * Defaults to {@code false}.
     * @return whether to use meter names as the New Relic eventType value
     */
    default boolean meterNameEventTypeEnabled() {
        String v = get(prefix() + ".meterNameEventTypeEnabled");
        return Boolean.parseBoolean(v);
    }

    /**
     * This configuration property will only be used if {@link #meterNameEventTypeEnabled()} is {@code false}.
     * Default value is {@code MicrometerSample}.
     * @return static eventType value to send to New Relic for all metrics.
     */
    default String eventType() {
        String v = get(prefix() + ".eventType");
        if (v == null)
            return "MicrometerSample";
        return v;
    }

    default String apiKey() {
        String v = get(prefix() + ".apiKey");
        if (v == null)
            throw new MissingRequiredConfigurationException("apiKey must be set to report metrics to New Relic");
        return v;
    }

    default String accountId() {
        String v = get(prefix() + ".accountId");
        if (v == null)
            throw new MissingRequiredConfigurationException("accountId must be set to report metrics to New Relic");
        return v;
    }

    /**
     * @return The URI for the New Relic insights API. The default is
     * {@code https://insights-collector.newrelic.com}. If you need to pass through
     * a proxy, you can change this value.
     */
    default String uri() {
        String v = get(prefix() + ".uri");
        return (v == null) ? "https://insights-collector.newrelic.com" : v;
    }
}
