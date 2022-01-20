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
package io.micrometer.api.instrument.simple;

import io.micrometer.api.instrument.config.MeterRegistryConfig;
import io.micrometer.api.instrument.config.MeterRegistryConfigValidator;
import io.micrometer.api.instrument.config.validate.PropertyValidator;
import io.micrometer.api.instrument.config.validate.Validated;

import java.time.Duration;

/**
 * Configuration for {@link SimpleMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface SimpleConfig extends MeterRegistryConfig {
    SimpleConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "simple";
    }

    /**
     * @return The step size (reporting frequency) to use.
     */
    default Duration step() {
        return PropertyValidator.getDuration(this, "step").orElse(Duration.ofMinutes(1));
    }

    /**
     * @return A mode that determines whether the registry reports cumulative values over all time or
     * a rate normalized form representing changes in the last {@link #step()}.
     */
    default CountingMode mode() {
        return PropertyValidator.getEnum(this, CountingMode.class, "mode").orElse(CountingMode.CUMULATIVE);
    }

    @Override
    default Validated<?> validate() {
        return MeterRegistryConfigValidator.checkAll(this,
                MeterRegistryConfigValidator.check("step", SimpleConfig::step),
                MeterRegistryConfigValidator.check("mode", SimpleConfig::mode)
        );
    }
}
