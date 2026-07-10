/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import java.time.Duration;

import static org.mockito.Mockito.mock;

class CloudWatchMeterRegistryCompatibilityTest extends MeterRegistryCompatibilityKit {

    private final CloudWatchConfig config = new CloudWatchConfig() {
        @Override
        public @Nullable String get(String key) {
            return null;
        }

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public String namespace() {
            return "DOESNOTMATTER";
        }
    };

    @Override
    public MeterRegistry registry() {
        // noinspection ConstantConditions
        return new CloudWatchMeterRegistry(config, new MockClock(), mock(CloudWatchAsyncClient.class));
    }

    @Override
    public Duration step() {
        return config.step();
    }

}
