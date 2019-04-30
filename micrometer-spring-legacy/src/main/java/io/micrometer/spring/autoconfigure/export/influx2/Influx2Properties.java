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
package io.micrometer.spring.autoconfigure.export.influx2;

import io.micrometer.spring.autoconfigure.export.properties.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Influx metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "management.metrics.export.influx2")
public class Influx2Properties extends StepRegistryProperties {

    /**
     * Specifies the destination bucket for writes.
     */
    private String bucket;

    /**
     * Specifies the destination organization for writes.
     */
    private String org;

    /**
     * Authenticate requests with this token.
     */
    private String token;

    /**
     * The URI for the Influx backend. The default is {@code http://localhost:8086/api/v2}.
     */
    private String uri = "http://localhost:8086/api/v2";

    /**
     * Whether to enable GZIP compression of metrics batches published to Influx.
     */
    private boolean compressed = true;

    /**
     * Whether to create the Influx bucket if it does not exist before attempting to
     * publish metrics to it.
     */
    private boolean autoCreateBucket = true;

    /**
     * The duration in seconds for how long data will be kept in the created bucket.
     */
    private Integer everySeconds;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(final String bucket) {
        this.bucket = bucket;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(final String org) {
        this.org = org;
    }

    public String getToken() {
        return token;
    }

    public void setToken(final String token) {
        this.token = token;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(final String uri) {
        this.uri = uri;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setCompressed(final boolean compressed) {
        this.compressed = compressed;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    public void setAutoCreateBucket(final boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public Integer getEverySeconds() {
        return everySeconds;
    }

    public void setEverySeconds(final Integer everySeconds) {
        this.everySeconds = everySeconds;
    }
}
