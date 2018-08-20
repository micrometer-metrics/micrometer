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
import io.micrometer.core.instrument.util.StringUtils;

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

    /**
     * Maximum allowed tags for Azure/
     */
    int ALLOWED_CUSTOM_DIMENSIONS = 10;

    /**
     * Possible externalization of properties according to Application Insights Java SDK support
     * Rethink : Should be matched with iKey property name in Application Insights Spring Boot starter
     */
    String EXTERNAL_PROPERTY_IKEY_NAME = "APPLICATION_INSIGHTS_IKEY";
    String EXTERNAL_PROPERTY_IKEY_NAME_SECONDARY = "APPINSIGHTS_INSTRUMENTATIONKEY";

    @Override
    default String prefix() {
        return AZURE_PREFIX;
    }

    /**
     * default implementation to get the instrumentation key from the config
     * @return Instrumentation Key
     */
    default String instrumentationKey() {
        String v = get(prefix() + ".instrumentation-key");

        if (!StringUtils.isBlank(v)) {
            return v;
        }

        v = System.getProperty(EXTERNAL_PROPERTY_IKEY_NAME);
        if (!StringUtils.isBlank(v)) {
            return v;
        }

        v = System.getProperty(EXTERNAL_PROPERTY_IKEY_NAME_SECONDARY);
        if (!StringUtils.isBlank(v)) {
            return v;
        }

        // Second, try to find the i-key as an environment variable 'APPLICATION_INSIGHTS_IKEY' or 'APPINSIGHTS_INSTRUMENTATIONKEY'
        v = System.getenv(EXTERNAL_PROPERTY_IKEY_NAME);
        if (!StringUtils.isBlank(v)) {
            return v;
        }
        v = System.getenv(EXTERNAL_PROPERTY_IKEY_NAME_SECONDARY);
        if (!StringUtils.isBlank(v)) {
            return v;
        }
        if (v == null)
            throw new MissingRequiredConfigurationException("instrumentationKey must be set to report metrics to Application Insights");
        return v;
    }

    /**
     * Returns the maximum allowed custom Dimensions in Azure Backend (currently not used in discussion)
     * @return Allowed Custom dimensions
     */
    default int getAllowedCustomDimensions() {
        return ALLOWED_CUSTOM_DIMENSIONS;
    }
}
