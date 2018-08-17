/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.azure;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;

/**
 * Configuration for {@link AzureMeterRegistry}
 * @author Dhaval Doshi
 */
public interface AzureConfig extends StepRegistryConfig {
    AzureConfig DEFAULT = k -> null;
    /**
     * Azure Monitor Prefix
     */
    String AZURE_PREFIX = "azure.application-insights";

    @Override
    default String prefix() {
        return AZURE_PREFIX;
    }

    /**
     * default implementation to get the instrumentation key from the config
     * @return
     */
    default String instrumentationKey() {
        String v = get(prefix() + ".instrumentation-key");
        if (v == null)
            throw new MissingRequiredConfigurationException("instrumentationKey must be set to report metrics to Application Insights");
        return v;
    }
}
