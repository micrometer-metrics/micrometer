/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.dynatrace2;

import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.util.function.Function;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;
import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.MAX_METRIC_LINES_PER_REQUEST;

/**
 * Configuration for {@link DynatraceMeterRegistry}
 *
 * @author Oriol Barcelona
 * @author David Mass
 */
public interface DynatraceConfig extends StepRegistryConfig {

    @Override
    default String prefix() {
        return "dynatrace2";
    }

    default String deviceName() { return getString(this,"deviceName").orElse(""); }

    default String groupName() { return getString(this,"groupName").orElse(""); }

    default String entityId() { return getString(this,"entityId").orElse(""); }

    default String apiToken() {
        if (this.uri().contains("localhost")){
            return getSecret(this, "apiToken").orElse("");
        }
        return getSecret(this, "apiToken").required().get();
    }

    default String uri() {
        return getUrlString(this, "uri").required().get();
    }

    @Override
    default int batchSize() {
        return getInteger(this, "batchSize").orElse(MAX_METRIC_LINES_PER_REQUEST);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                check("deviceName", DynatraceConfig::deviceName),
                check("groupName", DynatraceConfig::groupName),
                check("entityId", DynatraceConfig::entityId),
                check("apiToken", DynatraceConfig::apiToken),
                checkRequired("uri", DynatraceConfig::uri),
                check("batchSize", DynatraceConfig::batchSize)
                        .andThen(invalidateWhenGreaterThan(MAX_METRIC_LINES_PER_REQUEST))
        );
    }

    default Function<Validated<Integer>, Validated<Integer>> invalidateWhenGreaterThan(int value) {
        return v -> v.invalidateWhen(b -> b > value, "cannot be greater than " + value, InvalidReason.MALFORMED);
    }
}
