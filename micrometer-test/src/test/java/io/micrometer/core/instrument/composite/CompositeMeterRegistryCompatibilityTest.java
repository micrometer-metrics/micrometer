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
package io.micrometer.core.instrument.composite;

import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.MockClock;
import io.micrometer.api.instrument.composite.CompositeMeterRegistry;
import io.micrometer.api.instrument.simple.SimpleConfig;
import io.micrometer.api.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;

import java.time.Duration;

class CompositeMeterRegistryCompatibilityTest extends MeterRegistryCompatibilityKit {
    @Override
    public MeterRegistry registry() {
        return new CompositeMeterRegistry(new MockClock()) {{
            add(new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock));
        }};
    }

    @Override
    public Duration step() {
        return SimpleConfig.DEFAULT.step();
    }
}
