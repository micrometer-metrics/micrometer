/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.health;

import io.micrometer.core.instrument.config.MeterRegistryConfig;

import java.time.Duration;

import static io.micrometer.core.instrument.config.validate.PropertyValidator.getDuration;

/**
 * {@link MeterRegistryConfig} for {@link HealthMeterRegistry}.
 *
 * @author Jon Schneider
 * @since 1.6.0
 */
public interface HealthConfig extends MeterRegistryConfig {

    HealthConfig DEFAULT = key -> null;

    @Override
    default String prefix() {
        return "health";
    }

    /**
     * @return The step size to use.
     */
    default Duration step() {
        return getDuration(this, "step").orElse(Duration.ofSeconds(10));
    }

}
