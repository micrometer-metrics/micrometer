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
package io.micrometer.spring.autoconfigure.export.graphite;

import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.spring.autoconfigure.export.RegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * {@link ConfigurationProperties} for configuring Graphite metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "spring.metrics.graphite")
public class GraphiteProperties extends RegistryProperties implements GraphiteConfig {

    @Override
    public String prefix() {
        return "spring.metrics.graphite";
    }

    public void setStep(Duration step) {
        set("step", step);
    }

    public void setRateUnits(TimeUnit rateUnits) {
        set("rateUnits", rateUnits);
    }

    public void setDurationUnits(TimeUnit durationUnits) {
        set("durationUnits", durationUnits);
    }

    public void setHost(String host) {
        set("host", host);
    }

    public void setPort(Integer port) {
        set("port", port);
    }

    public void setEnabled(Boolean enabled) {
        set("enabled", enabled);
    }

}
