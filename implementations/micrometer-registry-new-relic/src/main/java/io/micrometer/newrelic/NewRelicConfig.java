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

import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

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
     * When {@code true}, the meter name will be used as the {@code eventType} for the NewRelic event.
     * When {@code false}, the {@code eventType} will be set to {@link #eventType()}.
     * <br><br>
     * Defaults to {@code false}.
     *
     * @return whether to use meter names as the NewRelic {@code eventType}
     */
    default boolean meterNameEventTypeEnabled() {
        return Boolean.parseBoolean(get(prefix() + ".meterNameEventTypeEnabled"));
    }

    /**
     * The {@code eventType} to use for events sent to NewRelic.
     * <br><br>
     * Ignored if {@link #meterNameEventTypeEnabled()} is {@code true}.
     * <br><br>
     * Defaults to {@code MicrometerSample}.
     *
     * @return static eventType value to send to New Relic for all metrics.
     */
    default String eventType() {
        String v = get(prefix() + ".eventType");
        return (v == null) ? "MicrometerSample" : v;
    }

    /**
     * The {@link NewRelicIntegration} method to use for reporting metric data.
     * <br><br>
     * Defaults to {@link NewRelicIntegration#API}.
     *
     * @return The NewRelic integration method
     */
    default NewRelicIntegration integration() {
        return NewRelicIntegration.fromString(get(prefix() + ".integration")).orElse(NewRelicIntegration.API);
    }

    /**
     * The <a href="https://docs.newrelic.com/docs/apis/get-started/intro-apis/types-new-relic-api-keys#event-insert-key">NewRelic Insights API key</a> to use.
     * <br><br>
     * Ignored when {@link #integration()} is not {@link NewRelicIntegration#API}.
     *
     * @return The NewRelic Insights API key
     */
    @Nullable
    default String apiKey() {
        return get(prefix() + ".apiKey");
    }

    /**
     * The ID of the NewRelic account to report Insights data to.
     * <br><br>
     * Ignored when {@link #integration()} is not {@link NewRelicIntegration#API}.
     *
     * @return The NewRelic account ID
     */
    @Nullable
    default String accountId() {
        return get(prefix() + ".accountId");
    }

    /**
     * The URI for the New Relic insights API to report events to.
     * The default is {@code https://insights-collector.newrelic.com}, but this can be changed if needed to pass data through a proxy.
     * <br><br>
     * Ignored when {@link #integration()} is not {@link NewRelicIntegration#API}.
     *
     * @return The NewRelic Insights API endpoint to use
     */
    @Nullable
    default String uri() {
        String v = get(prefix() + ".uri");
        return (v == null) ? "https://insights-collector.newrelic.com" : v;
    }
}
