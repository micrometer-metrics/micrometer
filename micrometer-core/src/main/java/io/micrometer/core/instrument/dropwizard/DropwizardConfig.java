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
package io.micrometer.core.instrument.dropwizard;

import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.core.instrument.config.validate.Validated;

import java.time.Duration;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.check;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getDuration;

/**
 * Base configuration for {@link DropwizardMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface DropwizardConfig extends MeterRegistryConfig {

    /**
     * @return The step size (reporting frequency, max decaying) to use. The default is 1
     * minute.
     */
    default Duration step() {
        return getDuration(this, "step").orElse(Duration.ofMinutes(1));
    }

    @Override
    default Validated<?> validate() {
        return validate(this);
    }

    /**
     * Validate a provided configuration.
     * @param config configuration to validate
     * @return validation result
     * @since 1.5.0
     */
    static Validated<?> validate(DropwizardConfig config) {
        return checkAll(config, check("step", DropwizardConfig::step));
    }

}
