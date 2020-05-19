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
package io.micrometer.influx;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link InfluxMeterRegistry}; since Micrometer 1.6, this also support the InfluxDB v2.
 *
 * @author Jon Schneider
 */
public interface InfluxConfig extends StepRegistryConfig {
    /**
     * Accept configuration defaults
     */
    InfluxConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "influx";
    }

    /**
     * @return The db to send metrics to. Defaults to "mydb".
     */
    default String db() {
        return getString(this, "db").orElse("mydb");
    }

    /**
     * @return Sets the write consistency for each point. The Influx default is 'one'. Must
     * be one of 'any', 'one', 'quorum', or 'all'. Only available for InfluxEnterprise clusters.
     */
    default InfluxConsistency consistency() {
        return getEnum(this, InfluxConsistency.class, "consistency").orElse(InfluxConsistency.ONE);
    }

    /**
     * @return Authenticate requests with this user. By default is {@code null}, and the registry will not
     * attempt to present credentials to Influx.
     */
    @Nullable
    default String userName() {
        return getSecret(this, "userName").orElse(null);
    }

    /**
     * @return Authenticate requests with this password. By default is {@code null}, and the registry will not
     * attempt to present credentials to Influx.
     */
    @Nullable
    default String password() {
        return getSecret(this, "password").orElse(null);
    }

    /**
     * @return Influx writes to the DEFAULT retention policy if one is not specified.
     */
    @Nullable
    default String retentionPolicy() {
        return getString(this, "retentionPolicy").orElse(null);
    }

    /**
     * @return Time period for which influx should retain data in the current database (e.g. 2h, 52w).
     */
    @Nullable
    default String retentionDuration() {
        return getString(this, "retentionDuration").orElse(null);
    }

    /**
     * @return How many copies of the data are stored in the cluster. Must be 1 for a single node instance.
     */
    @Nullable
    default Integer retentionReplicationFactor() {
        return getInteger(this, "retentionReplicationFactor").orElse(null);
    }

    /**
     * @return The time range covered by a shard group (e.g. 2h, 52w).
     */
    @Nullable
    default String retentionShardDuration() {
        return getString(this, "retentionShardDuration").orElse(null);
    }

    /**
     * @return The URI for the Influx backend. The default is {@code http://localhost:8086}.
     */
    default String uri() {
        return getUrlString(this, "uri").orElse("http://localhost:8086");
    }

    /**
     * @return {@code true} if metrics publish batches should be GZIP compressed, {@code false} otherwise.
     */
    default boolean compressed() {
        return getBoolean(this, "compressed").orElse(true);
    }

    /**
     * @return {@code true} if Micrometer should check if {@link #db()} exists before attempting to publish
     * metrics to it, creating it if it does not exist.
     */
    default boolean autoCreateDb() {
        return getBoolean(this, "autoCreateDb").orElse(true);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                checkRequired("db", InfluxConfig::db),
                checkRequired("consistency", InfluxConfig::consistency),
                checkRequired("uri", InfluxConfig::uri)
        );
    }
}
