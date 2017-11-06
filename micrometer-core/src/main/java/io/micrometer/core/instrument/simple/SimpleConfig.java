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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.time.Duration;

public interface SimpleConfig extends StepRegistryConfig {
    SimpleConfig DEFAULT = k -> null;

    // Useful in tests
    Duration DEFAULT_STEP = Duration.ofMinutes(1);

    @Override
    default String prefix() {
        return "simple";
    }

    /**
     * Returns the step size (reporting frequency) to use. The default is 10 seconds.
     */
    default Duration step() {
        String v = get(prefix() + ".step");
        return v == null ? DEFAULT_STEP : Duration.parse(v);
    }
}
