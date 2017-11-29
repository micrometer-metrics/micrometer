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
package io.micrometer.spring.autoconfigure.export.datadog;

import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Datadog metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "spring.metrics.datadog")
public class DatadogProperties extends StepRegistryProperties {
    /**
     * Your API key, found in your account settings at datadoghq. This property is required.
     */
    private String apiKey;

    /**
     * The tag that will be mapped to "host" when shipping metrics to datadog, or {@code null} if
     * host should be omitted on publishing.
     */
    private String hostKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getHostKey() {
        return hostKey;
    }

    public void setHostKey(String hostKey) {
        this.hostKey = hostKey;
    }
}
