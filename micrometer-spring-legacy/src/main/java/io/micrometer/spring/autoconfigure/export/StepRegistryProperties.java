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
package io.micrometer.spring.autoconfigure.export;

import java.time.Duration;

/**
 * Base configuration for a metrics registry that pushes aggregated
 * metrics on a regular interval.
 *
 * @author Jon Schneider
 * @author Andy Wilkinson
 */
public abstract class StepRegistryProperties {

    /**
     * The step size (reporting frequency) to use.
     */
    private Duration step = Duration.ofMinutes(1);

    /**
     * Enable publishing to the backend.
     */
    private Boolean enabled = true;

    /**
     * The connection timeout for requests to the backend.
     */
    private Duration connectTimeout;

    /**
     * The read timeout for requests to the backend.
     */
    private Duration readTimeout;

    /**
     * The number of threads to use with the metrics publishing scheduler.
     */
    private Integer numThreads;

    /**
     * The number of measurements per request to use for the backend. If more
     * measurements are found, then multiple requests will be made.
     */
    private Integer batchSize;

    public Duration getStep() {
        return step;
    }

    public void setStep(Duration step) {
        this.step = step;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Integer getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(Integer numThreads) {
        this.numThreads = numThreads;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }
}
