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

import io.micrometer.common.lang.Nullable;
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
     * Instrumentation key to use when sending metrics.
     * @return Instrumentation Key
     * @deprecated since 1.11.0, use {@link #connectionString()} instead. This method is
     * only called as a fallback in the default implementation if a connectionString is
     * not configured.
     */
    @Nullable
    @Deprecated
    default String instrumentationKey() {
        return getSecret(this, "instrumentationKey").get();
    }

    /**
     * Connection string to use when configuring sending metrics.
     * @return Connection String
     * @see <a
     * href=https://learn.microsoft.com/en-us/azure/azure-monitor/app/sdk-connection-string">Connection
     * strings</a>
     * @since 1.11.0
     */
    @Nullable
    default String connectionString() {
        return getSecret(this, "connectionString").orElseGet(() -> {
            String instrumentationKey = instrumentationKey();
            if (instrumentationKey == null) {
                return null;
            }
            return "InstrumentationKey=" + instrumentationKey;
        });
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c),
                check("connectionString", AzureMonitorConfig::connectionString));
    }

}
