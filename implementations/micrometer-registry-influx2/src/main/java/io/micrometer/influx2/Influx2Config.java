/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.influx2;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getBoolean;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getInteger;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getUrlString;

/**
 * Configuration for {@link Influx2MeterRegistry}.
 *
 * @author Jakub Bednar
 */
public interface Influx2Config extends StepRegistryConfig {

    /**
     * Accept configuration defaults
     */
    Influx2Config DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "influx2";
    }

    /**
     * @return Specifies the destination bucket for writes.
     */
    default String bucket() {
        return getString(this, "bucket").orElse(null);
    }

    /**
     * @return Specifies the destination organization for writes.
     */
    default String org() {
        return getString(this, "org").orElse(null);
    }

    /**
     * @return Authenticate requests with this token.
     */
    default String token() {
        return getString(this, "token").orElse(null);
    }

    /**
     * @return The URI for the Influx backend. The default is {@code http://localhost:8086/api/v2}.
     */
    default String uri() {
        return getUrlString(this, "uri").orElse("http://localhost:8086/api/v2");
    }

    /**
     * @return {@code true} if metrics publish batches should be GZIP compressed, {@code false} otherwise.
     */
    default boolean compressed() {
        return getBoolean(this, "compressed").orElse(true);
    }

    /**
     * @return {@code true} if Micrometer should check if {@link #bucket()} exists before attempting to publish
     * metrics to it, creating it if it does not exist.
     */
    default boolean autoCreateBucket() {
        return getBoolean(this, "autoCreateBucket").orElse(true);
    }

    /**
     * @return The duration in seconds for how long data will be kept in the created bucket.
     */
    @Nullable
    default Integer everySeconds() {
        return getInteger(this, "everySeconds").orElse(null);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                checkRequired("bucket", Influx2Config::bucket),
                checkRequired("org", Influx2Config::org),
                checkRequired("token", Influx2Config::token) ,
                checkRequired("uri", Influx2Config::uri)
        );
    }
}
