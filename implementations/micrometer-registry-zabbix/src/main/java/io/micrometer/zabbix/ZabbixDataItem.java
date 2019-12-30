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
package io.micrometer.zabbix;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;

/**
 * Store a data item in a Zabbix compatible format
 */
public class ZabbixDataItem {

    private long clock;
    private String host;
    private String key;
    private double value;
    private Meter.Id id;
    private String metricName;
    private Iterable<Tag> tags;
    private String keySuffix;

    public ZabbixDataItem() {
    }

    public ZabbixDataItem(final long clock, final String host, final String key, final double value,
                          final Meter.Id id, final String metricName, final Iterable<Tag> tags, final String keySuffix) {
        this.clock = clock;
        this.host = host;
        this.key = key;
        this.value = value;
        this.id = id;
        this.metricName = metricName;
        this.tags = tags;
        this.keySuffix = keySuffix;
    }

    static public ZabbixDataItem.Builder builder() {
        return new ZabbixDataItem.Builder();
    }

    public long getClock() {
        return clock;
    }

    public void setClock(long clock) {
        this.clock = clock;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public Meter.Id getId() {
        return id;
    }

    public void setId(final Meter.Id id) {
        this.id = id;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(final String metricName) {
        this.metricName = metricName;
    }

    public Iterable<Tag> getTags() {
        return tags;
    }

    public void setTags(final Iterable<Tag> tags) {
        this.tags = tags;
    }

    public String getKeySuffix() {
        return keySuffix;
    }

    public void setKeySuffix(final String keySuffix) {
        this.keySuffix = keySuffix;
    }

    public static class Builder {
        private Long clock;
        private String host;
        private String key;
        private double value;
        private Meter.Id id;
        private String metricName;
        private Iterable<Tag> tags;
        private String keySuffix;

        private Builder() {
        }

        public Builder clock(long clock) {
            this.clock = clock;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder value(double value) {
            this.value = value;
            return this;
        }

        public Builder id(final Meter.Id id) {
            this.id = id;
            return this;
        }

        public Builder metricName(final String metricName) {
            this.metricName = metricName;
            return this;
        }

        public Builder tags(final Iterable<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public Builder keySuffix(final String keySuffix) {
            this.keySuffix = keySuffix;
            return this;
        }

        public ZabbixDataItem build() {
            if (clock == null) {
                clock = System.currentTimeMillis() / 1000;
            }
            return new ZabbixDataItem(clock, host, key, value, id, metricName, tags, keySuffix);
        }

    }

}
