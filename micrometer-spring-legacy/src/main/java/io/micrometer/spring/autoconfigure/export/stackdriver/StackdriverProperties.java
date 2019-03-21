/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure.export.stackdriver;

import io.micrometer.spring.autoconfigure.export.properties.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * {@link ConfigurationProperties} for configuring metrics export to Stackdriver.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "management.metrics.export.stackdriver")
public class StackdriverProperties extends StepRegistryProperties {

    /**
     * Step size (i.e. reporting frequency) to use.
     */
    private Duration step = Duration.ofSeconds(10);

    /**
     * Google Cloud project id.
     */
    private String projectId;

    /**
     * Location of JSON representation of Google Cloud service account credentials
     * with at least write access to the monitoring API.
     */
    private Resource serviceAccountCredentials;

    @Override
    public Duration getStep() {
        return this.step;
    }

    @Override
    public void setStep(Duration step) {
        this.step = step;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Resource getServiceAccountCredentials() {
        return serviceAccountCredentials;
    }

    public void setServiceAccountCredentials(Resource serviceAccountCredentials) {
        this.serviceAccountCredentials = serviceAccountCredentials;
    }
}
