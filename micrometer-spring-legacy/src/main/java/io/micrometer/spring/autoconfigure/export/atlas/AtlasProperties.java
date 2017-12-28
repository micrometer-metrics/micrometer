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
package io.micrometer.spring.autoconfigure.export.atlas;

import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@link ConfigurationProperties} for configuring Atlas metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "management.metrics.export.atlas")
public class AtlasProperties extends StepRegistryProperties {
    /**
     * The URI for the Atlas backend
     */
    private String uri;

    /**
     * The TTL for meters that do not have any activity. After this period the meter
     * will be considered expired and will not get reported.
     */
    private Duration meterTimeToLive;

    /**
     * Enable streaming to Atlas LWC.
     */
    private Boolean lwcEnabled;

    /**
     * The frequency for refreshing config settings from the LWC service.
     */
    private Duration configRefreshFrequency;

    /**
     * The TTL for subscriptions from the LWC service
     */
    private Duration configTimeToLive;

    /**
     * The URI for the Atlas LWC endpoint to retrieve current subscriptions.
     */
    private String configUri;

    /**
     * The URI for the Atlas LWC endpoint to evaluate the data for a subscription.
     */
    private String evalUri;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Duration getMeterTimeToLive() {
        return meterTimeToLive;
    }

    public void setMeterTimeToLive(Duration meterTimeToLive) {
        this.meterTimeToLive = meterTimeToLive;
    }

    public Boolean getLwcEnabled() {
        return lwcEnabled;
    }

    public void setLwcEnabled(Boolean lwcEnabled) {
        this.lwcEnabled = lwcEnabled;
    }

    public Duration getConfigRefreshFrequency() {
        return configRefreshFrequency;
    }

    public void setConfigRefreshFrequency(Duration configRefreshFrequency) {
        this.configRefreshFrequency = configRefreshFrequency;
    }

    public Duration getConfigTimeToLive() {
        return configTimeToLive;
    }

    public void setConfigTimeToLive(Duration configTimeToLive) {
        this.configTimeToLive = configTimeToLive;
    }

    public String getConfigUri() {
        return configUri;
    }

    public void setConfigUri(String configUri) {
        this.configUri = configUri;
    }

    public String getEvalUri() {
        return evalUri;
    }

    public void setEvalUri(String evalUri) {
        this.evalUri = evalUri;
    }
}
