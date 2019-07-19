/**
 * Copyright 2018 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.stackdriver;

import com.google.api.MonitoredResource;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;

/**
 * {@link StepRegistryConfig} for Stackdriver.
 *
 * @author Jon Schneider
 * @since 1.1.0
 */
public interface StackdriverConfig extends StepRegistryConfig {
    /**
     * The "global" type is meant as a catch-all when no other resource type is suitable, which
     * includes everything that Micrometer ships.
     * https://cloud.google.com/monitoring/custom-metrics/creating-metrics#which-resource
     */
    String RESOURCE_TYPE = "global";

    @Override
    default String prefix() {
        return "stackdriver";
    }

    default String projectId() {
        String v = get(prefix() + ".projectId");
        if (v == null)
            throw new MissingRequiredConfigurationException("projectId must be set to report metrics to Stackdriver");
        return v;
    }

    default MonitoredResource monitoredResource() {
        return MonitoredResource.newBuilder()
                .setType(RESOURCE_TYPE)
                .putLabels("project_id", projectId())
                .build();
    }
}
