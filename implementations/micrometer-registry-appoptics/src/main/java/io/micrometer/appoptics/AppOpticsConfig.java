/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.appoptics;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;

/**
 * Configuration for {@link AppOpticsMeterRegistry}.
 *
 * @author Hunter Sherman
 * @since 1.1.0
 */
public interface AppOpticsConfig extends StepRegistryConfig {

    //https://docs.appoptics.com/api/#create-a-measurement
    int MAX_BATCH_SIZE = 1000;
    int DEFAULT_BATCH_SIZE = 500;

    @Override
    default String prefix() {
        return "appoptics";
    }

    /**
     * @return AppOptics API token
     */
    default String apiToken() {
        String t = get(prefix() + ".apiToken");
        if (t == null)
            throw new MissingRequiredConfigurationException("apiToken must be set to report metrics to AppOptics");
        return t;
    }

    /**
     * @return The tag that will be mapped to {@literal @host} when shipping metrics to AppOptics.
     */
    @Nullable
    default String hostTag() {
        String v = get(prefix() + ".hostTag");
        return v == null ? "instance" : v;
    }

    /**
     * @return the URI to ship metrics to
     */
    default String uri() {
        String v = get(prefix() + ".uri");
        return v == null ? "https://api.appoptics.com/v1/measurements" : v;
    }

    @Override
    default int batchSize() {
        String v = get(prefix() + ".batchSize");
        return v == null ? DEFAULT_BATCH_SIZE : Math.min(Integer.parseInt(v), MAX_BATCH_SIZE);
    }

    @Deprecated
    @Override
    default Duration connectTimeout() {
        String v = get(prefix() + ".connectTimeout");
        // AppOptics regularly times out when the default is 1 second.
        return v == null ? Duration.ofSeconds(5) : Duration.parse(v);
    }
}
