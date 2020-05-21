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

import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.StringUtils;
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
     * Since Micrometer 1.6, this also support the InfluxDB v2.
     * For the InfluxDB v2 the value should be an amount of seconds (e.g. 3600).
     *
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
     * @return {@code true} if Micrometer should check if {@link #db()} or {@link #bucket()} exists before attempting to publish
     * metrics to it, creating it if it does not exist. Since Micrometer 1.6, this also support the InfluxDB v2.
     */
    default boolean autoCreateDb() {
        return getBoolean(this, "autoCreateDb").orElse(true);
    }

    /**
     * Specifies the destination organization for writes. Takes either the ID or Name interchangeably.
     * See detail info: <a href="https://v2.docs.influxdata.com/v2.0/organizations/view-orgs/">How to retrieve the <i>org</i> parameter in the InfluxDB UI.</a>
     * @return The destination organization for writes.
     * @since 1.6
     */
    @Nullable
    default String org() {
        return getString(this, "org").orElse(null);
    }

    /**
     * Specifies the destination bucket for writes. Takes either the ID or Name interchangeably.
     * See detail info: <a href="https://v2.docs.influxdata.com/v2.0/organizations/buckets/view-buckets/">How to retrieve the <i>bucket</i> parameter in the InfluxDB UI.</a>
     * @return The destination organization for writes.
     * @since 1.6
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

            return Validated.invalid(valid.getProperty(), bucket, "db or bucket should be specified", InvalidReason.MISSING);
        }).get();
    }

    /**
     * See detail info for the InfluxDB v1 and InfluxDB v2:
     * <ul>
     *     <li><a href="https://docs.influxdata.com/influxdb/v1.8/administration/authentication_and_authorization#3-include-the-token-in-http-requests">InfluxDB v1: Include the token in HTTP requests</a></li>
     *     <li><a href="https://v2.docs.influxdata.com/v2.0/reference/api/#authentication">InfluxDB v2: Authentication API</a></li>
     * </ul>
     * @return Authentication token to authorize API requests.
     * @since 1.6
     */
    @Nullable
    default String token() {
        return getString(this, "token").orElse(null);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                checkRequired("db", InfluxConfig::db),
                checkRequired("bucket", InfluxConfig::bucket),
                checkRequired("consistency", InfluxConfig::consistency),
                checkRequired("uri", InfluxConfig::uri)
        );
    }
}
