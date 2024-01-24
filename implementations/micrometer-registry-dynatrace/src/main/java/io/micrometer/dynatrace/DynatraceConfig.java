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
package io.micrometer.dynatrace;

import com.dynatrace.file.util.DynatraceFileBasedConfigurationProvider;
import com.dynatrace.metric.util.DynatraceMetricApiConstants;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.util.Collections;
import java.util.Map;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;
import static io.micrometer.dynatrace.DynatraceApiVersion.V1;
import static io.micrometer.dynatrace.DynatraceApiVersion.V2;

/**
 * Configuration for {@link DynatraceMeterRegistry}
 *
 * @author Oriol Barcelona
 * @author Georg Pirklbauer
 * @since 1.1.0
 */
public interface DynatraceConfig extends StepRegistryConfig {

    /**
     * Accept configuration defaults.
     * @since 1.10.0
     */
    DynatraceConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "dynatrace";
    }

    default String apiToken() {
        Validated<String> secret = getSecret(this, "apiToken");
        if (apiVersion() == V1) {
            return secret.required().get();
        }

        return secret.orElse(
                // Local OneAgent does not require a token.
                uri().equals(DynatraceMetricApiConstants.getDefaultOneAgentEndpoint()) ? ""
                        : DynatraceFileBasedConfigurationProvider.getInstance().getMetricIngestToken());
    }

    default String uri() {
        Validated<String> uri = getUrlString(this, "uri");
        if (apiVersion() == V1) {
            return uri.required().get();
        }

        return uri.orElse(DynatraceFileBasedConfigurationProvider.getInstance().getMetricIngestEndpoint());
    }

    default String deviceId() {
        return getString(this, "deviceId").orElse("");
    }

    default String technologyType() {
        return getSecret(this, "technologyType").orElse("java");
    }

    /**
     * Return device group name.
     * @return device group name
     * @since 1.2.0
     */
    @Nullable
    default String group() {
        return get(prefix() + ".group");
    }

    /**
     * Return the version of the target Dynatrace API. Defaults to v1 if not provided.
     * @return a {@link DynatraceApiVersion} containing the version of the targeted
     * Dynatrace API.
     * @since 1.8.0
     */
    default DynatraceApiVersion apiVersion() {
        // If a device id is specified, use v1 as default. If it is not, use v2.
        // The version can be overwritten explicitly when creating a MM config
        // For Spring Boot, v1 is automatically chosen when the device id is set.
        return getEnum(this, DynatraceApiVersion.class, "apiVersion").orElse(deviceId().isEmpty() ? V2 : V1);
    }

    /**
     * Return metric key prefix.
     * @return metric key prefix
     * @since 1.8.0
     */
    default String metricKeyPrefix() {
        return getString(this, "metricKeyPrefix").orElse("");
    }

    /**
     * Return default dimensions.
     * @return default dimensions
     * @since 1.8.0
     */
    default Map<String, String> defaultDimensions() {
        return Collections.emptyMap();
    }

    /**
     * Return whether to enrich with Dynatrace metadata. Dynatrace metadata is provided by
     * the Dynatrace OneAgent or Dynatrace Kubernetes Operator and helps put metrics
     * emitted via the Micrometer exporter in context more easily.
     * @return whether to enrich with Dynatrace metadata
     * @since 1.8.0
     */
    default boolean enrichWithDynatraceMetadata() {
        if (apiVersion() == V1) {
            return false;
        }
        return getBoolean(this, "enrichWithDynatraceMetadata").orElse(true);
    }

    /**
     * Return whether to fall back to the built-in micrometer instruments for Timer and
     * DistributionSummary.
     * @return {@code true} if the resetting Dynatrace instruments should be used, and
     * {@code false} if the registry should fall back to the built-in Micrometer
     * instruments.
     * @since 1.9.0
     */
    default boolean useDynatraceSummaryInstruments() {
        if (apiVersion() == V1) {
            return false;
        }
        return getBoolean(this, "useDynatraceSummaryInstruments").orElse(true);
    }

    /**
     * Toggle whether to export meter metadata (unit and description) to the Dynatrace
     * backend for the V2 version of this exporter. Metadata will be exported by default
     * from Micrometer version 1.12.0. This setting has no effect for the (legacy)
     * Dynatrace Exporter v1. Setting this toggle to {@code false} has a similar effect to
     * registering a MeterFilter that removes unit and description from all registered
     * meters.
     * @return true if metadata should be exported, false otherwise.
     * @since 1.12.0
     */
    default boolean exportMeterMetadata() {
        if (apiVersion() == V1) {
            return false;
        }
        return getBoolean(this, "exportMeterMetadata").orElse(true);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, config -> StepRegistryConfig.validate(config),
                checkRequired("apiVersion", DynatraceConfig::apiVersion).andThen(apiVersionValidation -> {
                    if (apiVersionValidation.isValid()) {
                        return checkAll(this, config -> {
                            if (config.apiVersion() == V1) {
                                return checkAll(this, checkRequired("apiToken", DynatraceConfig::apiToken),
                                        checkRequired("uri", DynatraceConfig::uri),
                                        check("deviceId", DynatraceConfig::deviceId).andThen(Validated::nonBlank),
                                        check("technologyType", DynatraceConfig::technologyType)
                                            .andThen(Validated::nonBlank));
                            }
                            else {
                                return checkAll(this, checkRequired("uri", DynatraceConfig::uri));
                            }
                        });
                    }
                    else {
                        return apiVersionValidation;
                    }
                }));
    }

}
