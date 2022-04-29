/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.push.PushRegistryConfig;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getUrlString;

/**
 * Config for {@link OtlpMeterRegistry}.
 *
 * @author Tommy Ludwig
 * @since 1.9.0
 */
public interface OtlpConfig extends PushRegistryConfig {

    /**
     * Configuration with default values.
     */
    OtlpConfig DEFAULT = key -> null;

    @Override
    default String prefix() {
        return "otlp";
    }

    /**
     * Defaults to http://localhost:4318/v1/metrics
     * @return address to where metrics will be published.
     */
    default String url() {
        return getUrlString(this, "url").orElse("http://localhost:4318/v1/metrics");
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> PushRegistryConfig.validate(c),
                checkRequired("url", OtlpConfig::url)
        );
    }
}
