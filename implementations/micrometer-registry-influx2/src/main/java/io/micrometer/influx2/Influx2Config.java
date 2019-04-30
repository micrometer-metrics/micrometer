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

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

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
        String v = get(prefix() + ".bucket");
        if (v == null)
            throw new MissingRequiredConfigurationException("bucket must be set to report metrics to InfluxDB");
        return v;
    }

    /**
     * @return Specifies the destination organization for writes.
     */
    default String org() {
        String v = get(prefix() + ".org");
        if (v == null)
            throw new MissingRequiredConfigurationException("org must be set to report metrics to InfluxDB");
        return v;
    }

    /**
     * @return Authenticate requests with this token.
     */
    default String token() {
        String v = get(prefix() + ".token");
        if (v == null)
            throw new MissingRequiredConfigurationException("token must be set to report metrics to InfluxDB");
        return v;
    }

    /**
     * @return The URI for the Influx backend. The default is {@code http://localhost:8086/api/v2}.
     */
    default String uri() {
        String v = get(prefix() + ".uri");
        return (v == null) ? "http://localhost:8086/api/v2" : v;
    }

    /**
     * @return {@code true} if metrics publish batches should be GZIP compressed, {@code false} otherwise.
     */
    default boolean compressed() {
        String v = get(prefix() + ".compressed");
        return v == null || Boolean.valueOf(v);
    }

    /**
     * @return {@code true} if Micrometer should check if {@link #bucket()} exists before attempting to publish
     * metrics to it, creating it if it does not exist.
     */
    default boolean autoCreateBucket() {
        String v = get(prefix() + ".autoCreateBucket");
        return v == null || Boolean.valueOf(v);
    }

    /**
     * @return The duration in seconds for how long data will be kept in the created bucket.
     */
    @Nullable
    default Integer everySeconds() {
        String v = get(prefix() + ".everySeconds");
        return v == null ? null : Integer.parseInt(v);
    }
}
