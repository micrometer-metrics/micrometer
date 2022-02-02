/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.tck;

import java.time.Duration;

import io.micrometer.api.instrument.observation.ObservationRegistry;
import io.micrometer.api.instrument.observation.SimpleObservationRegistry;
import io.micrometer.api.instrument.simple.SimpleConfig;

class ObservationRegistryCompatibilityKitTests extends ObservationRegistryCompatibilityKit {

    ObservationRegistry registry = new SimpleObservationRegistry();

    @Override
    public ObservationRegistry registry() {
        return this.registry;
    }

    @Override
    public Duration step() {
        return SimpleConfig.DEFAULT.step();
    }
}
