/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.dynatrace;

import com.dynatrace.metric.util.DynatraceMetricApiConstants;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import java.util.Collections;
import java.util.Map;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link DynatraceMeterRegistry}
 *
 * @author Oriol Barcelona
 * @author Georg Pirklbauer
 * @since 1.1.0
 */
public interface DynatraceConfig extends StepRegistryConfig {

    @Override
    default String prefix() {
        return "dynatrace";
    }

    default String apiToken() {
        Validated<String> secret = getSecret(this, "apiToken");
        if (apiVersion() == DynatraceApiVersion.V1) {
            return secret.required().get();
        }
        return secret.orElse("");
    }

    default String uri() {
        Validated<String> uri = getUrlString(this, "uri");
        if (apiVersion() == DynatraceApiVersion.V1) {
            return uri.required().get();
        }
        return uri.orElse(DynatraceMetricApiConstants.getDefaultOneAgentEndpoint());
    }

    default String deviceId() {
        return getString(this, "deviceId").required().get();
    }

    default String technologyType() {
        return getSecret(this, "technologyType").orElse("java");
    }

    /**
     * Return device group name.
     *
     * @return device group name
     * @since 1.2.0
     */
    @Nullable
    default String group() {
        return get(prefix() + ".group");
    }

    /**
     * Return the version of the target Dynatrace API. Defaults to v1 if not provided.
     *
     * @return a {@link DynatraceApiVersion} containing the version of the targeted Dynatrace API.
     * @since 1.8.0
     */
    default DynatraceApiVersion apiVersion() {
        // if not specified, defaults to v1 for backwards compatibility.
        return getEnum(this, DynatraceApiVersion.class, "apiVersion").orElse(DynatraceApiVersion.V1);
    }

    /**
     * Return metric key prefix.
     *
     * @return metric key prefix
     * @since 1.8.0
     */
    default String metricKeyPrefix() {
        return getString(this, "metricKeyPrefix").orElse("");
    }

    /**
     * Return default dimensions.
     *
     * @return default dimensions
     * @since 1.8.0
     */
    default Map<String, String> defaultDimensions() {
        return Collections.emptyMap();
    }

    /**
     * Return whether to enrich with Dynatrace metadata.
     *
     * @return whether to enrich with Dynatrace metadata
     * @since 1.8.0
     */
    default boolean enrichWithDynatraceMetadata() {
        return getBoolean(this, "enrichWithDynatraceMetadata").orElse(true);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                config -> StepRegistryConfig.validate(config),
                checkRequired("apiVersion", DynatraceConfig::apiVersion).andThen(
                        apiVersionValidation -> {
                            if (apiVersionValidation.isValid()) {
                                return checkAll(this,
                                        config -> {
                                            if (config.apiVersion() == DynatraceApiVersion.V1) {
                                                return checkAll(this,
                                                        checkRequired("apiToken", DynatraceConfig::apiToken),
                                                        checkRequired("uri", DynatraceConfig::uri),
                                                        checkRequired("deviceId", DynatraceConfig::deviceId),
                                                        check("technologyType", DynatraceConfig::technologyType).andThen(Validated::nonBlank)
                                                );
                                            } else {
                                                return checkAll(this,
                                                        checkRequired("uri", DynatraceConfig::uri)
                                                );
                                            }
                                        }
                                );
                            } else {
                                return apiVersionValidation;
                            }
                        })
        );
    }
}
