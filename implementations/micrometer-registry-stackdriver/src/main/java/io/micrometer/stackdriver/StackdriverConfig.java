/**
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.stackdriver;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
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
     *
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
     * Return {@link CredentialsProvider} to use.
     *
     * @return {@code CredentialsProvider} to use
     * @since 1.4.0
     */
    default CredentialsProvider credentials() {
        return getString(this, "credentials")
                .flatMap((credentials, valid) -> {
                    if (StringUtils.isBlank(credentials)) {
                        return Validated.valid(valid.getProperty(), MetricServiceSettings.defaultCredentialsProviderBuilder().build());
                    }

                    try {
                        FixedCredentialsProvider provider = FixedCredentialsProvider.create(
                                GoogleCredentials.fromStream(new FileInputStream(credentials))
                                        .createScoped(MetricServiceSettings.getDefaultServiceScopes())
                        );
                        return Validated.valid(valid.getProperty(), provider);
                    } catch (IOException t) {
                        return Validated.invalid(valid.getProperty(), credentials, "cannot read credentials file", InvalidReason.MALFORMED, t);
                    }
                })
                .get();
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                checkRequired("projectId", StackdriverConfig::projectId),
                checkRequired("resourceLabels", StackdriverConfig::resourceLabels),
                checkRequired("resourceType", StackdriverConfig::resourceType),
                checkRequired("credentials", StackdriverConfig::credentials)
        );
    }
}
