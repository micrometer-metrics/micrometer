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
package io.micrometer.azuremonitor;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.check;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getSecret;

/**
 * Configuration for {@link AzureMonitorMeterRegistry}.
 *
 * @author Dhaval Doshi
 * @since 1.1.0
 */
public interface AzureMonitorConfig extends StepRegistryConfig {

    @Override
    default String prefix() {
        return "azuremonitor";
    }

    /**
     * default implementation to get the instrumentation key from the config
     * @return Instrumentation Key
     */
    default String instrumentationKey() {
        return getSecret(this, "instrumentationKey").get();
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c),
                check("instrumentationKey", AzureMonitorConfig::instrumentationKey));
    }

}
