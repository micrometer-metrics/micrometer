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
package io.micrometer.spring.autoconfigure.export.influx;

import io.micrometer.influx.InfluxConsistency;
import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Influx metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "management.metrics.export.influx")
public class InfluxProperties extends StepRegistryProperties {

    /**
     * Tag that will be mapped to "host" when shipping metrics to Influx. Can be
     * omitted if host should be omitted on publishing.
     */
    private String db;

    /**
     * Write consistency for each point.
     */
    private InfluxConsistency consistency;

    /**
     * Login user of the Influx server.
     */
    private String userName;

    /**
     * Login password of the Influx server.
     */
    private String password;

    /**
     * Retention policy to use (Influx writes to the DEFAULT retention policy if one is
     * not specified).
     */
    private String retentionPolicy;

    /**
     * URI of the Influx server.
     */
    private String uri;

    /**
     * Enable GZIP compression of metrics batches published to Influx.
     */
    private Boolean compressed;

    /**
     * Check if Influx database exists before attempting to publish metrics to it, creating it if it does not exist.
     */
    private Boolean autoCreateDb;

    /**
     * Time period for which influx should retain data in the current database (e.g. 2h, 52w)
     */
    private String retentionDuration;

    /**
     * How many copies of the data are stored in the cluster. Must be 1 for a single node instance.
     */
    private Integer retentionReplicationFactor;

    /**
     * The time range covered by a shard group (e.g. 2h, 52w).
     */
    private String retentionShardDuration;

    public String getDb() {
        return this.db;
    }

    public void setDb(String db) {
        this.db = db;
    }

    public InfluxConsistency getConsistency() {
        return this.consistency;
    }

    public void setConsistency(InfluxConsistency consistency) {
        this.consistency = consistency;
    }

    public String getUserName() {
        return this.userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRetentionPolicy() {
        return this.retentionPolicy;
    }

    public void setRetentionPolicy(String retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public String getUri() {
        return this.uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Boolean getCompressed() {
        return this.compressed;
    }

    public void setCompressed(Boolean compressed) {
        this.compressed = compressed;
    }

    public Boolean getAutoCreateDb() {
        return autoCreateDb;
    }

    public void setAutoCreateDb(Boolean autoCreateDb) {
        this.autoCreateDb = autoCreateDb;
    }

    public String getRetentionDuration() {
        return retentionDuration;
    }

    public void setRetentionDuration(String retentionDuration) {
        this.retentionDuration = retentionDuration;
    }

    public Integer getRetentionReplicationFactor() {
        return retentionReplicationFactor;
    }

    public void setRetentionReplicationFactor(Integer retentionReplicationFactor) {
        this.retentionReplicationFactor = retentionReplicationFactor;
    }

    public String getRetentionShardDuration() {
        return retentionShardDuration;
    }

    public void setRetentionShardDuration(String retentionShardDuration) {
        this.retentionShardDuration = retentionShardDuration;
    }
}
