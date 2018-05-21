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
package io.micrometer.spring.autoconfigure.export.dynatrace;


import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Dynatrace metrics export.
 *
 * @author Oriol Barcelona
 */
@ConfigurationProperties(prefix = "management.metrics.export.dynatrace")
public class DynatraceProperties  extends StepRegistryProperties {

    /**
     * Dynatrace API token
     */
    private String apiToken;

    /**
     * URI to ship metrics to. Should be used for SaaS, self managed
     * instances or to en-route through an internal proxy
     */
    private String uri;

    /**
     * The custom device used to identify the sender into Dynatrace
     */
    private String deviceId;

    /**
     * The type of technology used to create the custom device and its metrics
     */
    private String technologyType;

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getTechnologyType() {
        return technologyType;
    }

    public void setTechnologyType(String technologyType) {
        this.technologyType = technologyType;
    }
}
