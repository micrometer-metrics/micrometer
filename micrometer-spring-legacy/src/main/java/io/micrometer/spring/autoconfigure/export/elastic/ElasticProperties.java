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
package io.micrometer.spring.autoconfigure.export.elastic;

import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Elastic metrics export.
 *
 * @author Nicolas Portmann
 */
@ConfigurationProperties(prefix = "management.metrics.export.elastic")
public class ElasticProperties extends StepRegistryProperties {

    /**
     * The hosts to send the metrics to
     */
    private String[] hosts;

    /**
     * The index name to write metrics to.
     */
    private String index;

    /**
     * The index date format used for rolling indices.
     * This is appended to the index name, split by a '-'.
     */
    private String indexDateFormat;

    /**
     * The bulk size per request.
     */
    private int bulkSize;

    /**
     * The name of the timestamp field.
     */
    private String timestampFieldName;

    /**
     * Whether to create the index automatically if it doesn't exist.
     */
    private boolean autoCreateIndex;

    /**
     * The Basic Authentication username.
     */
    private String userName;

    /**
     * The Basic Authentication password.
     */
    private String password;

    public String[] getHosts() {
        return hosts;
    }

    public void setHosts(String[] hosts) {
        this.hosts = hosts;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getIndexDateFormat() {
        return indexDateFormat;
    }

    public void setIndexDateFormat(String indexDateFormat) {
        this.indexDateFormat = indexDateFormat;
    }

    public int getBulkSize() {
        return bulkSize;
    }

    public void setBulkSize(int bulkSize) {
        this.bulkSize = bulkSize;
    }

    public String getTimestampFieldName() {
        return timestampFieldName;
    }

    public void setTimestampFieldName(String timestampFieldName) {
        this.timestampFieldName = timestampFieldName;
    }

    public boolean isAutoCreateIndex() {
        return autoCreateIndex;
    }

    public void setAutoCreateIndex(boolean autoCreateIndex) {
        this.autoCreateIndex = autoCreateIndex;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
