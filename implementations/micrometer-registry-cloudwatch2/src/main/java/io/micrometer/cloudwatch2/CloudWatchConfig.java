/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.check;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getInteger;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;

/**
 * Configuration for CloudWatch exporting.
 *
 * @author Dawid Kublik
 * @since 1.2.0
 */
public interface CloudWatchConfig extends StepRegistryConfig {
    int MAX_BATCH_SIZE = 20;

    @Override
    default String prefix() {
        return "cloudwatch";
    }

    default String namespace() {
        return getString(this, "namespace").required().get();
    }

    @Override
    default int batchSize() {
        return Math.min(getInteger(this, "batchSize").orElse(MAX_BATCH_SIZE), MAX_BATCH_SIZE);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                (CloudWatchConfig c) -> StepRegistryConfig.validate(c),
                checkRequired("namespace", CloudWatchConfig::namespace),
                check("batchSize", CloudWatchConfig::batchSize)
                        .andThen(v -> v.invalidateWhen(b -> b > MAX_BATCH_SIZE, "cannot be greater than " + MAX_BATCH_SIZE,
                                InvalidReason.MALFORMED))
        );
    }
}
