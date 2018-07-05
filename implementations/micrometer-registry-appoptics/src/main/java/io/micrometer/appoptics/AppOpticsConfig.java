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
package io.micrometer.appoptics;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;
import org.apache.commons.lang.StringUtils;

/**
 * Configuration for {@link AppOpticsMeterRegistry}.
 *
 * @author Hunter Sherman
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
    default String token() {
        final String v = get(prefix() + ".token");
        if (v == null)
            throw new MissingRequiredConfigurationException("apiKey must be set to report metrics to AppOptics");
        return v + ":";
    }

    /**
     * @return source identifier (usually hostname)
     */
    @Nullable
    default String source() {
        return StringUtils.defaultIfEmpty(
            get(prefix() + ".source"),
            "instance"
        );
    }

    /**
     * @return the URI to ship metrics to
     */
    default String uri() {
        return StringUtils.defaultIfEmpty(
            get(prefix() + ".uri"),
            "https://api.appoptics.com/v1/measurements"
        );
    }

    /**
     * @return optional (string), prepended to metric names
     */
    default String metricPrefix() {

        return StringUtils.defaultIfEmpty(
            get(prefix() + ".metricPrefix"),
            ""
        );
    }

    @Override
    default int batchSize() {
        final String v = get(prefix() + ".batchSize");
        return v == null ? DEFAULT_BATCH_SIZE : Math.min(Integer.parseInt(v), MAX_BATCH_SIZE);
    }
}
