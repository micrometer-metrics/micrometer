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

import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;

public abstract class DefaultStepRegistryConfig implements StepRegistryConfig {
    private final StepRegistryProperties props;

    private final StepRegistryConfig defaults = new StepRegistryConfig() {
        @Override
        public String prefix() {
            return "doesnotmatter";
        }

        @Override
        @Nullable
        public String get(String k) {
            return null;
        }
    };

    public DefaultStepRegistryConfig(StepRegistryProperties props) {
        this.props = props;
    }

    @Override
    public String prefix() {
        return "doesnotmatter";
    }

    @Override
    @Nullable
    public String get(String k) {
        return null;
    }

    @Override
    @Nullable
    public Duration step() {
        return props.getStep();
    }

    @Override
    public boolean enabled() {
        return props.getEnabled();
    }

    @Override
    public Duration connectTimeout() {
        return props.getConnectTimeout() == null ? defaults.connectTimeout() : props.getConnectTimeout();
    }

    @Override
    public Duration readTimeout() {
        return props.getReadTimeout() == null ? defaults.readTimeout() : props.getReadTimeout();
    }

    @Override
    public int numThreads() {
        return props.getNumThreads() == null ? defaults.numThreads() : props.getNumThreads();
    }

    @Override
    public int batchSize() {
        return props.getBatchSize() == null ? defaults.batchSize() : props.getBatchSize();
    }
}
