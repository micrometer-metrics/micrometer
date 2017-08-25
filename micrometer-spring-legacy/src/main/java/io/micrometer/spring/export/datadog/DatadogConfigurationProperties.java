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
package io.micrometer.spring.export.datadog;

import io.micrometer.datadog.DatadogConfig;
import io.micrometer.spring.export.StepRegistryConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "metrics.datadog")
public class DatadogConfigurationProperties extends StepRegistryConfigurationProperties implements DatadogConfig {
    public void setApiKey(String apiKey) {
        set("apiKey", apiKey);
    }

    public void setHostTag(String hostTag) {
        set("hostTag", hostTag);
    }

    @Override
    public String prefix() {
        return "metrics.datadog";
    }
}
