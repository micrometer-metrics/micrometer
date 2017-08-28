/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("metrics")
public class MetricsConfigurationProperties {
    private Web web = new Web();

    /**
     * Determines whether {@link MeterRegistry} implementations configured by Spring should be
     * bound to the global static registry on {@link io.micrometer.core.instrument.Metrics}.
     * For Spring Boot tests involving metrics, set this to {@code false} to maximize test independence.
     * Otherwise, it can be left to {@code true}.
     */
    private Boolean useGlobalRegistry = true;

    public Boolean getUseGlobalRegistry() {
        return useGlobalRegistry;
    }

    public void setUseGlobalRegistry(Boolean useGlobalRegistry) {
        this.useGlobalRegistry = useGlobalRegistry;
    }

    public static class Web {
        /**
         * Determines whether every request mapping (WebMVC or Webflux) should be automatically timed.
         * If the number of time series emitted from a Spring application grows too large on account
         * of request mapping timings, disable this and use {@link io.micrometer.core.annotation.Timed}
         * on a per request mapping basis as needed.
         */
        private Boolean autoTimeServerRequests = true;

        private String serverRequestsName = "http.server.requests";

        private String clientRequestsName = "http.client.requests";

        public Boolean getAutoTimeServerRequests() {
            return autoTimeServerRequests;
        }

        public void setAutoTimeServerRequests(Boolean autoTimeServerRequests) {
            this.autoTimeServerRequests = autoTimeServerRequests;
        }

        public void setServerRequestsName(String serverRequestsName) {
            this.serverRequestsName = serverRequestsName;
        }

        public String getServerRequestsName() {
            return serverRequestsName;
        }

        public void setClientRequestsName(String clientRequestsName) {
            this.clientRequestsName = clientRequestsName;
        }

        public String getClientRequestsName() {
            return clientRequestsName;
        }
    }

    public Web getWeb() {
        return web;
    }
}
