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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.StringUtils;

/**
 * Configuration for {@link DynatraceMeterRegistry}
 *
 * @author Oriol Barcelona
 */
public interface DynatraceConfig extends StepRegistryConfig {

    DynatraceConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "dynatrace";
    }

    default String apiToken() {
        String v = get(prefix() + ".apiToken");
        if (v == null)
            throw new MissingRequiredConfigurationException("apiToken must be set to report metrics to Dynatrace");
        return v;
    }

    default String uri() {
        String v = get(prefix() + ".uri");
        if (v == null)
            throw new MissingRequiredConfigurationException("uri must be set to report metrics to Dynatrace");
        return v;
    }

    default String deviceId() {
        String v = get(prefix() + ".deviceId");
        if (v == null)
            throw new MissingRequiredConfigurationException("deviceId must be set to report metrics to Dynatrace");
        return v;
    }

    default String technologyType() {
        String v = get(prefix() + ".technologyType");
        if (StringUtils.isEmpty(v))
            return "java";

        return v;
    }
}
