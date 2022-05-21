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
package io.micrometer.appoptics;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.time.Duration;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link AppOpticsMeterRegistry}.
 *
 * @author Hunter Sherman
 * @since 1.1.0
 */
public interface AppOpticsConfig extends StepRegistryConfig {

    // https://docs.appoptics.com/api/#create-a-measurement
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
        return getSecret(this, "apiToken").required().get();
    }

    /**
     * @return The tag that will be mapped to {@literal @host} when shipping metrics to
     * AppOptics.
     */
    @Nullable
    default String hostTag() {
        return getString(this, "hostTag").orElse("instance");
    }

    /**
     * @return the URI to ship metrics to
     */
    default String uri() {
        return getUrlString(this, "uri").orElse("https://api.appoptics.com/v1/measurements");
    }

    /**
     * @return whether or not to ship a floored time - floors time to the multiple of the
     * {@link #step()}
     */
    default boolean floorTimes() {
        return getBoolean(this, "floorTimes").orElse(false);
    }

    @Override
    default int batchSize() {
        return Math.min(getInteger(this, "batchSize").orElse(DEFAULT_BATCH_SIZE), MAX_BATCH_SIZE);
    }

    @Deprecated
    @Override
    default Duration connectTimeout() {
        // AppOptics regularly times out when the default is 1 second.
        return getDuration(this, "connectTimeout").orElse(Duration.ofSeconds(5));
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c), checkRequired("apiToken", AppOpticsConfig::apiToken),
                checkRequired("uri", AppOpticsConfig::uri),
                check("batchSize", AppOpticsConfig::batchSize).andThen(v -> v.invalidateWhen(b -> b > MAX_BATCH_SIZE,
                        "cannot be greater than " + MAX_BATCH_SIZE, InvalidReason.MALFORMED)));
    }

}
