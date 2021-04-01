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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;

import java.util.function.Function;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link DynatraceMeterRegistry}
 *
 * @author Oriol Barcelona
 */
public interface DynatraceConfig extends StepRegistryConfig {

    @Override
    default String prefix() {
        return "dynatrace";
    }

    default String apiToken() {
        return getSecret(this, "apiToken").required().get();
    }

    default String uri() {
        return getUrlString(this, "uri").required().get();
    }

    default String deviceId() {
        return getString(this, "deviceId").required().get();
    }

    default String technologyType() {
        return getSecret(this, "technologyType")
                .map(v -> StringUtils.isEmpty(v) ? "java" : v)
                .get();
    }

    /**
     * Return device group name.
     *
     * @return device group name
     * @since 1.2.0
     */
    @Nullable
    default String group() {
        return get(prefix() + ".group");
    }

    /**
     * Return the version of the target Dynatrace API.
     *
     * @return a {@link String} containing the version of the targeted Dynatrace API.
     */
    default String apiVersion() {
        return "v1";
//        return getString(this, "apiVersion").required().get();
    }

    @Override
    default Validated<?> validate() {
        // once the apiVersion is actually transmitted by spring-boot, use this:
//        Function<DynatraceConfig, Validated<String>> versionFunc = checkRequired("apiVersion", DynatraceConfig::apiVersion);
//        String versionStr = versionFunc.apply(this).get();
        String versionStr = "v1";
        if (versionStr.startsWith("v1")) {
            return checkAll(this,
                    c -> StepRegistryConfig.validate(c),
                    checkRequired("apiToken", DynatraceConfig::apiToken),
                    checkRequired("uri", DynatraceConfig::uri),
                    checkRequired("deviceId", DynatraceConfig::deviceId),
                    check("technologyType", DynatraceConfig::technologyType).andThen(Validated::nonBlank)
            );
        }
        
        // else check only api token and uri which are needed for v2. 
        throw new IllegalArgumentException(String.format("the api version is not valid: %s", versionStr));
    }
}
