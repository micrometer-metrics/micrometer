/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.core.instrument.push;

import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.ipc.http.HttpSender;

import java.time.Duration;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.check;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Common configuration settings for any registry that pushes aggregated metrics on a
 * regular interval.
 *
 * @author Jon Schneider
 */
public interface PushRegistryConfig extends MeterRegistryConfig {

    /**
     * @return The step size (reporting frequency) to use. The default is 1 minute.
     */
    default Duration step() {
        return getDuration(this, "step").orElse(Duration.ofMinutes(1));
    }

    /**
     * @return {@code true} if publishing is enabled. Default is {@code true}.
     */
    default boolean enabled() {
        return getBoolean(this, "enabled").orElse(true);
    }

    /**
     * Return the number of threads to use with the scheduler.
     * <p>
     * Note that this configuration is NOT supported.
     * @return The number of threads to use with the scheduler. The default is 2 threads.
     * @deprecated since 1.1.13 because this configuration is not used
     */
    @Deprecated
    default int numThreads() {
        return getInteger(this, "numThreads").orElse(2);
    }

    /**
     * @return The connection timeout for requests to the backend. The default is 1
     * second.
     * @deprecated Connect timeout and read timeout have different meanings depending on
     * the HTTP client. Configure timeout options on your {@link HttpSender} of choice
     * instead.
     */
    @Deprecated
    default Duration connectTimeout() {
        return getDuration(this, "connectTimeout").orElse(Duration.ofSeconds(1));
    }

    /**
     * @return The read timeout for requests to the backend. The default is 10 seconds.
     * @deprecated Connect timeout and read timeout have different meanings depending on
     * the HTTP client. Configure timeout options on your {@link HttpSender} of choice
     * instead.
     */
    @Deprecated
    default Duration readTimeout() {
        return getDuration(this, "readTimeout").orElse(Duration.ofSeconds(10));
    }

    /**
     * @return The number of measurements per request to use for the backend. If more
     * measurements are found, then multiple requests will be made. The default is 10,000.
     */
    default int batchSize() {
        return getInteger(this, "batchSize").orElse(10000);
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
    static Validated<?> validate(PushRegistryConfig config) {
        return checkAll(config, check("step", PushRegistryConfig::step),
                check("connectTimeout", PushRegistryConfig::connectTimeout),
                check("readTimeout", PushRegistryConfig::readTimeout),
                check("batchSize", PushRegistryConfig::batchSize), check("numThreads", PushRegistryConfig::numThreads));
    }

}
