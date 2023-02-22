/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.influx;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link InfluxMeterRegistry}. Since Micrometer 1.7, InfluxDB v2 and v1
 * are supported.
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
     * Must be one of 'any', 'one', 'quorum', or 'all'. Only available for
     * InfluxEnterprise clusters.
     * @return Sets the write consistency for each point. The default is 'one'.
     */
    default InfluxConsistency consistency() {
        return getEnum(this, InfluxConsistency.class, "consistency").orElse(InfluxConsistency.ONE);
    }

    /**
     * Authentication by 'userName' and 'password' is not supported for InfluxDB v2.
     * @return Authenticate requests with this user. By default is {@code null}, and the
     * registry will not attempt to present credentials to Influx.
     */
    @Nullable
    default String userName() {
        return getSecret(this, "userName").orElse(null);
    }

    /**
     * Authentication by 'userName' and 'password' is not supported for InfluxDB v2.
     * @return Authenticate requests with this password. By default is {@code null}, and
     * the registry will not attempt to present credentials to Influx.
     */
    @Nullable
    default String password() {
        return getSecret(this, "password").orElse(null);
    }

    /**
     * This retention configuration will only be used with InfluxDB v1.
     * @return Influx writes to the DEFAULT retention policy if one is not specified.
     */
    @Nullable
    default String retentionPolicy() {
        return getString(this, "retentionPolicy").orElse(null);
    }

    /**
     * This retention configuration will only be used with InfluxDB v1.
     * @return Time period for which influx should retain data in the current database
     * (e.g. 2h, 52w).
     */
    @Nullable
    default String retentionDuration() {
        return getString(this, "retentionDuration").orElse(null);
    }

    /**
     * This retention configuration will only be used with InfluxDB v1.
     * @return How many copies of the data are stored in the cluster. Must be 1 for a
     * single node instance.
     */
    @Nullable
    default Integer retentionReplicationFactor() {
        return getInteger(this, "retentionReplicationFactor").orElse(null);
    }

    /**
     * This retention configuration will only be used with InfluxDB v1.
     * @return The time range covered by a shard group (e.g. 2h, 52w).
     */
    @Nullable
    default String retentionShardDuration() {
        return getString(this, "retentionShardDuration").orElse(null);
    }

    /**
     * @return The URI for the Influx backend. The default is
     * {@code http://localhost:8086}.
     */
    default String uri() {
        return getUrlString(this, "uri").orElse("http://localhost:8086");
    }

    /**
     * @return {@code true} if metrics publish batches should be GZIP compressed,
     * {@code false} otherwise.
     */
    default boolean compressed() {
        return getBoolean(this, "compressed").orElse(true);
    }

    /**
     * Auto-creating the database is only supported with InfluxDB v1.
     * @return {@code true} if Micrometer should check if {@link #db()} exists before
     * attempting to publish metrics to it, creating it if it does not exist.
     */
    default boolean autoCreateDb() {
        return getBoolean(this, "autoCreateDb").orElse(true);
    }

    /**
     * Specifies the API version used to send metrics. Use 'v1' or 'v2' based on the
     * version of your InfluxDB. Defaults to 'v1' unless an {@link #org()} is configured.
     * If an {@link #org()} is configured, defaults to 'v2'.
     * @return The API version used to send metrics.
     * @since 1.7
     */
    default InfluxApiVersion apiVersion() {
        return getEnum(this, InfluxApiVersion.class, "apiVersion").orElseGet(() -> {
            if (StringUtils.isNotBlank(org())) {
                return InfluxApiVersion.V2;
            }
            return InfluxApiVersion.V1;
        });
    }

    /**
     * Specifies the destination organization for writes. Takes either the ID or Name
     * interchangeably. This is only used with InfluxDB v2.
     * @return The destination organization for writes.
     * @since 1.7
     * @see <a href="https://v2.docs.influxdata.com/v2.0/organizations/view-orgs/">How to
     * retrieve the org parameter in the InfluxDB UI.</a>
     */
    @Nullable
    default String org() {
        return getString(this, "org").orElse(null);
    }

    /**
     * Specifies the destination bucket for writes. Takes either the ID or Name
     * interchangeably. This is only used with InfluxDB v2.
     * @return The destination bucket (or db) for writes.
     * @since 1.7
     * @see <a href=
     * "https://v2.docs.influxdata.com/v2.0/organizations/buckets/view-buckets/">How to
     * retrieve the bucket parameter in the InfluxDB UI.</a>
     */
    default String bucket() {
        return getString(this, "bucket").flatMap((bucket, valid) -> {
            if (StringUtils.isNotBlank(bucket)) {
                return Validated.valid(valid.getProperty(), bucket);
            }

            String db = db();
            if (StringUtils.isNotBlank(db)) {
                return Validated.valid(valid.getProperty(), db);
            }

            return Validated.invalid(valid.getProperty(), bucket, "db or bucket should be specified",
                    InvalidReason.MISSING);
        }).get();
    }

    /**
     * Authentication token for the InfluxDB API. This takes precedence over
     * userName/password if configured.
     * @return Authentication token to authorize API requests.
     * @since 1.7
     * @see <a href=
     * "https://docs.influxdata.com/influxdb/v1.8/administration/authentication_and_authorization#3-include-the-token-in-http-requests">InfluxDB
     * v1: Include the token in HTTP requests</a>
     * @see <a href=
     * "https://v2.docs.influxdata.com/v2.0/reference/api/#authentication">InfluxDB v2:
     * Authentication API</a>
     */
    @Nullable
    default String token() {
        return getString(this, "token").orElse(null);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c), checkRequired("db", InfluxConfig::db),
                checkRequired("bucket", InfluxConfig::bucket), checkRequired("consistency", InfluxConfig::consistency),
                checkRequired("apiVersion", InfluxConfig::apiVersion)
                    .andThen(v -> v.invalidateWhen(a -> a == InfluxApiVersion.V2 && StringUtils.isBlank(org()),
                            "requires 'org' is also configured", InvalidReason.MISSING))
                    .andThen(v -> v.invalidateWhen(a -> a == InfluxApiVersion.V2 && StringUtils.isBlank(token()),
                            "requires 'token' is also configured", InvalidReason.MISSING)),
                checkRequired("uri", InfluxConfig::uri));
    }

}
