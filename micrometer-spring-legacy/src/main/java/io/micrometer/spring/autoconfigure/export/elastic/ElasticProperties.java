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

import java.util.concurrent.TimeUnit;

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
     * Prefix all metrics with a given {@link String}.
     */
    private String metricPrefix;

    /**
     * Convert all durations to a certain {@link TimeUnit}
     */
    private TimeUnit rateUnits;

    /**
     * Convert all durations to a certain {@link TimeUnit}
     */
    private TimeUnit durationUnits;

    /**
     * The timeout to wait for until a connection attempt is and the next host is tried.
     */
    private int timeout;

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
    private String timeStampFieldName;

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

    public String getMetricPrefix() {
        return metricPrefix;
    }

    public void setMetricPrefix(String metricPrefix) {
        this.metricPrefix = metricPrefix;
    }

    public TimeUnit getRateUnits() {
        return rateUnits;
    }

    public void setRateUnits(TimeUnit rateUnits) {
        this.rateUnits = rateUnits;
    }

    public TimeUnit getDurationUnits() {
        return durationUnits;
    }

    public void setDurationUnits(TimeUnit durationUnits) {
        this.durationUnits = durationUnits;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
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

    public String getTimeStampFieldName() {
        return timeStampFieldName;
    }

    public void setTimeStampFieldName(String timeStampFieldName) {
        this.timeStampFieldName = timeStampFieldName;
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
