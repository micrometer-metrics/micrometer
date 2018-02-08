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

import io.micrometer.core.annotation.Incubating;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

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
    private Duration step = Duration.ofMinutes(1);

    /**
     * Configuration options for using Prometheus Pushgateway, allowing metrics to be pushed
     * when they cannot be scraped.
     */
    private PushgatewayProperties pushgateway = new PushgatewayProperties();

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

    public PushgatewayProperties getPushgateway() {
        return pushgateway;
    }

    public void setPushgateway(PushgatewayProperties pushgateway) {
        this.pushgateway = pushgateway;
    }

    /**
     * Configuration options for push-based interaction with Prometheus.
     */
    @Incubating(since = "1.0.0")
    public static class PushgatewayProperties {
        /**
         * Enable publishing via a Prometheus Pushgateway.
         */
        private Boolean enabled = false;

        /**
         * Required host:port or ip:port of the Pushgateway.
         */
        private String baseUrl = "localhost:9091";

        /**
         * Required identifier for this application instance.
         */
        private String job;

        /**
         * Frequency with which to push metrics to Pushgateway.
         */
        private Duration pushRate = Duration.ofMinutes(1);

        /**
         * Push metrics right before shut-down. Mostly useful for batch jobs.
         */
        private boolean pushOnShutdown = true;

        /**
         * Delete metrics from Pushgateway when application is shut-down
         */
        private boolean deleteOnShutdown = true;

        /**
         * Used to group metrics in pushgateway. A common example is setting
         */
        private Map<String, String> groupingKeys = new HashMap<>();

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getJob() {
            return job;
        }

        public void setJob(String job) {
            this.job = job;
        }

        public Duration getPushRate() {
            return pushRate;
        }

        public void setPushRate(Duration pushRate) {
            this.pushRate = pushRate;
        }
        
        public boolean isPushOnShutdown() {
            return pushOnShutdown;
        }
        
        public void setPushOnShutdown(boolean pushOnShutdown) {
            this.pushOnShutdown = pushOnShutdown;
        }

        public boolean isDeleteOnShutdown() {
            return deleteOnShutdown;
        }

        public void setDeleteOnShutdown(boolean deleteOnShutdown) {
            this.deleteOnShutdown = deleteOnShutdown;
        }

        public Map<String, String> getGroupingKeys() {
            return groupingKeys;
        }

        public void setGroupingKeys(Map<String, String> groupingKeys) {
            this.groupingKeys = groupingKeys;
        }
    }
}
