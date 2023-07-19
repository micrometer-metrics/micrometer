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
package io.micrometer.kairos;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getSecret;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getUrlString;

/**
 * Configuration for {@link KairosMeterRegistry}.
 *
 * @author Anton Ilinchik
 * @since 1.1.0
 */
public interface KairosConfig extends StepRegistryConfig {

    /**
     * Accept configuration defaults
     */
    KairosConfig DEFAULT = k -> null;

    /**
     * Property prefix to prepend to configuration names.
     * @return property prefix
     */
    default String prefix() {
        return "kairos";
    }

    /**
     * The URI to send the metrics to.
     * @return uri
     */
    default String uri() {
        return getUrlString(this, "uri").orElse("http://localhost:8080/api/v1/datapoints");
    }

    /**
     * @return Authenticate requests with this user. By default is {@code null}, and the
     * registry will not attempt to present credentials to KairosDB.
     */
    @Nullable
    default String userName() {
        return getSecret(this, "userName").orElse(null);
    }

    /**
     * @return Authenticate requests with this password. By default is {@code null}, and
     * the registry will not attempt to present credentials to KairosDB.
     */
    @Nullable
    default String password() {
        return getSecret(this, "password").orElse(null);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c), checkRequired("uri", KairosConfig::uri));
    }

}
