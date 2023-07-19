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
package io.micrometer.stackdriver;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getBoolean;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;

/**
 * {@link StepRegistryConfig} for Stackdriver.
 *
 * @author Jon Schneider
 * @since 1.1.0
 */
public interface StackdriverConfig extends StepRegistryConfig {

    @Override
    default String prefix() {
        return "stackdriver";
    }

    default String projectId() {
        return getString(this, "projectId").required().get();
    }

    /**
     * Return resource labels.
     * @return resource labels.
     * @since 1.4.0
     */
    default Map<String, String> resourceLabels() {
        return Collections.emptyMap();
    }

    default String resourceType() {
        return getString(this, "resourceType").orElse("global");
    }

    /**
     * Whether to use semantically correct metric types. This is {@code false} by default
     * for the sake of backwards compatibility. For example, when this is {@code false},
     * counter metrics are published as the GAUGE MetricKind. When this is {@code true},
     * counter metrics are published as the CUMULATIVE MetricKind.
     * <p>
     * If you have published metrics to Stackdriver before, switching this flag will cause
     * metrics publishing to fail until you delete the old MetricDescriptor with the
     * previous MetricKind so that it can be recreated with the new MetricKind next time
     * that metric is published. For example, the <a href=
     * "https://cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.metricDescriptors/delete">projects.metricDescriptors.delete
     * API</a> can be used to delete an existing MetricDescriptor.
     * @return a flag indicating if semantically correct metric types will be used
     * @since 1.8.0
     */
    default boolean useSemanticMetricTypes() {
        return getBoolean(this, "useSemanticMetricTypes").orElse(false);
    }

    /**
     * Return metric type prefix.
     * <p>
     * Available prefixes defined in
     * <a href= "https://cloud.google.com/monitoring/custom-metrics#identifier">Google
     * Cloud documentation</a>.
     * @return a prefix for MetricType
     * @since 1.10.0
     */
    default String metricTypePrefix() {
        return getString(this, "metricTypePrefix").orElse("custom.googleapis.com/");
    }

    /**
     * Return {@link CredentialsProvider} to use.
     * @return {@code CredentialsProvider} to use
     * @since 1.4.0
     */
    default CredentialsProvider credentials() {
        return getString(this, "credentials").flatMap((credentials, valid) -> {
            if (StringUtils.isBlank(credentials)) {
                return Validated.valid(valid.getProperty(),
                        MetricServiceSettings.defaultCredentialsProviderBuilder().build());
            }

            try {
                FixedCredentialsProvider provider = FixedCredentialsProvider
                    .create(GoogleCredentials.fromStream(new FileInputStream(credentials))
                        .createScoped(MetricServiceSettings.getDefaultServiceScopes()));
                return Validated.valid(valid.getProperty(), provider);
            }
            catch (IOException t) {
                return Validated.invalid(valid.getProperty(), credentials, "cannot read credentials file",
                        InvalidReason.MALFORMED, t);
            }
        }).get();
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c),
                checkRequired("projectId", StackdriverConfig::projectId),
                checkRequired("resourceLabels", StackdriverConfig::resourceLabels),
                checkRequired("resourceType", StackdriverConfig::resourceType),
                checkRequired("credentials", StackdriverConfig::credentials));
    }

}
