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
package io.micrometer.core.instrument.dropwizard;

import io.micrometer.core.instrument.config.MeterRegistryConfig;

import java.time.Duration;

/**
 * Base configuration for {@link DropwizardMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface DropwizardConfig extends MeterRegistryConfig {
    /**
     * @return The step size (reporting frequency, max decaying) to use. The default is 1 minute.
     */
    default Duration step() {
        String v = get(prefix() + ".step");
        return v == null ? Duration.ofMinutes(1) : Duration.parse(v);
    }
}
