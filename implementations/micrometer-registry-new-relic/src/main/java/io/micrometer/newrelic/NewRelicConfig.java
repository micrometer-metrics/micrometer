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
package io.micrometer.newrelic;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import static io.micrometer.common.util.StringUtils.isBlank;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link NewRelicMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Neil Powell
 * @since 1.0.0
 */
public interface NewRelicConfig extends StepRegistryConfig {

    @Override
    default String prefix() {
        return "newrelic";
    }

    /**
     * When this is {@code false}, the New Relic eventType value will be set to
     * {@link #eventType()}. Otherwise, the meter name will be used. Defaults to
     * {@code false}.
     * @return whether to use meter names as the New Relic eventType value
     */
    default boolean meterNameEventTypeEnabled() {
        return getBoolean(this, "meterNameEventTypeEnabled").orElse(false);
    }

    /**
     * This configuration property will only be used if
     * {@link #meterNameEventTypeEnabled()} is {@code false}. Default value is
     * {@code MicrometerSample}.
     * @return static eventType value to send to New Relic for all metrics.
     */
    default String eventType() {
        return getString(this, "eventType").orElse("MicrometerSample");
    }

    /**
     * When this is {@code INSIGHTS_AGENT}, the New Relic metrics will be published with
     * the {@link NewRelicInsightsAgentClientProvider} which delegates to the Java agent.
     * Defaults to {@code INSIGHTS_API} for publishing with the
     * {@link NewRelicInsightsApiClientProvider} to the Insights REST API.
     * @return the ClientProviderType to use
     */
    default ClientProviderType clientProviderType() {
        return getEnum(this, ClientProviderType.class, "clientProviderType").orElse(ClientProviderType.INSIGHTS_API);
    }

    @Nullable
    default String apiKey() {
        return getSecret(this, "apiKey")
            .invalidateWhen(secret -> isBlank(secret) && ClientProviderType.INSIGHTS_API.equals(clientProviderType()),
                    "is required when publishing to Insights API", InvalidReason.MISSING)
            .orElse(null);
    }

    @Nullable
    default String accountId() {
        return getSecret(this, "accountId")
            .invalidateWhen(secret -> isBlank(secret) && ClientProviderType.INSIGHTS_API.equals(clientProviderType()),
                    "is required when publishing to Insights API", InvalidReason.MISSING)
            .orElse(null);
    }

    /**
     * @return The URI for the New Relic insights API. The default is
     * {@code https://insights-collector.newrelic.com}. If you need to pass through a
     * proxy, you can change this value.
     */
    default String uri() {
        return getUrlString(this, "uri").orElse("https://insights-collector.newrelic.com");
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c), check("eventType", NewRelicConfig::eventType)
            .andThen(v -> v.invalidateWhen(type -> isBlank(type) && !meterNameEventTypeEnabled(),
                    "event type is required when not using the meter name as the event type", InvalidReason.MISSING)),
                checkRequired("clientProviderType", NewRelicConfig::clientProviderType));
    }

    default Validated<?> validateForInsightsApi() {
        return checkAll(this, c -> validate(),
                check("uri", NewRelicConfig::uri).andThen(v -> v.invalidateWhen(StringUtils::isBlank,
                        "is required when publishing to Insights API", InvalidReason.MISSING)),
                check("apiKey", NewRelicConfig::apiKey).andThen(v -> v.invalidateWhen(StringUtils::isBlank,
                        "is required when publishing to Insights API", InvalidReason.MISSING)),
                check("accountId", NewRelicConfig::accountId).andThen(v -> v.invalidateWhen(StringUtils::isBlank,
                        "is required when publishing to Insights API", InvalidReason.MISSING)));
    }

}
