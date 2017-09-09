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
package io.micrometer.spring.export.prometheus;


import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.spring.export.RegistryConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Exists solely to aid in autocompletion of Prometheus enablement in .properties and .yml.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "metrics.prometheus")
public class PrometheusConfigurationProperties extends RegistryConfigurationProperties implements PrometheusConfig {
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setDescriptions(Boolean descriptions) {
        set("descriptions", descriptions);
    }

    @Override
    public String prefix() {
        return "metrics.prometheus";
    }
}
