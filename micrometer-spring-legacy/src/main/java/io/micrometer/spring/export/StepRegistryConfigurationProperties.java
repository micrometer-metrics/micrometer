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
package io.micrometer.spring.export;

import io.micrometer.core.instrument.spectator.step.StepRegistryConfig;

import java.time.Duration;

public abstract class StepRegistryConfigurationProperties extends RegistryConfigurationProperties implements StepRegistryConfig {
    public void setStep(Duration step) {
        set("step", step);
    }

    public void setEnabled(Boolean enabled) {
        set("enabled", enabled);
    }

    public void setBatchSize(Integer batchSize) {
        set("batchSize", batchSize);
    }

    public void setConnectTimeout(Duration connectTimeout) {
        set("connectTimeout", connectTimeout);
    }

    public void setReadTimeout(Duration readTimeout) {
        set("readTimeout", readTimeout);
    }

    public void setNumThreads(Integer numThreads) {
        set("numThreads", numThreads);
    }
}
