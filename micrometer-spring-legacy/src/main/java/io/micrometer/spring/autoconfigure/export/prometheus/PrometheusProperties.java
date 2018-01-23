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
package io.micrometer.spring.autoconfigure.export.prometheus;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@link ConfigurationProperties} for configuring metrics export to Prometheus.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "management.metrics.export.prometheus")
public class PrometheusProperties {

    /**
     * Enable publishing to Prometheus.
     */
    private Boolean enabled;

    /**
     * Enable publishing descriptions as part of the scrape payload to Prometheus. Turn
     * this off to minimize the amount of data sent on each scrape.
     */
    private Boolean descriptions;

    /**
     * Step size (i.e. reporting frequency) to use.
     */
    private Duration step;

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getDescriptions() {
        return this.descriptions;
    }

    public void setDescriptions(Boolean descriptions) {
        this.descriptions = descriptions;
    }

    public Duration getStep() {
        return this.step;
    }

    public void setStep(Duration step) {
        this.step = step;
    }

}
