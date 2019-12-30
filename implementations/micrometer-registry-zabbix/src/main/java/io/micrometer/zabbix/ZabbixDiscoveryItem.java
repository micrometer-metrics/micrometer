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

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ZabbixDiscoveryItem {
    /**
     * Key for the discovery item.
     *
     * @see <a href="https://www.zabbix.com/documentation/current/manual/discovery/low_level_discovery">Zabbix low level discovery</a>
     */
    private String key;

    /**
     * The actual discovery item values that will be send to Zabbix.
     */
    private Set<ZabbixDiscoveryItemConfiguration> values;

    /**
     * Last time this discovery item was sent to Zabbix.
     */
    private OffsetDateTime lastSent;

    public ZabbixDiscoveryItem(String key, Set<ZabbixDiscoveryItemConfiguration> values) {
        this.key = key;
        this.values = values;
    }

    public static ZabbixDiscoveryItem.Builder builder() {
        return new ZabbixDiscoveryItem.Builder();
    }

    public String getKey() {
        return this.key;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    public Set<ZabbixDiscoveryItemConfiguration> getValues() {
        return values;
    }

    public void setValues(final SortedSet<ZabbixDiscoveryItemConfiguration> values) {
        this.values = values;
    }

    public OffsetDateTime getLastSent() {
        return lastSent;
    }

    public void setLastSent(final OffsetDateTime lastSent) {
        this.lastSent = lastSent;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        final ZabbixDiscoveryItem that = (ZabbixDiscoveryItem) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "ZabbixDiscoveryItem{" + "key='" + key + '\'' + ", values=" + values + ", lastSent=" + lastSent + '}';
    }

    /**
     * Builder for {@link ZabbixDiscoveryItem}
     */
    public static class Builder {
        private String key;
        private Set<ZabbixDiscoveryItemConfiguration> values = new HashSet<>();

        Builder() {
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder addValue(ZabbixDiscoveryItemConfiguration value) {
            this.values.add(value);
            return this;
        }

        public ZabbixDiscoveryItem build() {
            return new ZabbixDiscoveryItem(this.key, this.values);
        }
    }

    /**
     * Build {@link ZabbixDiscoveryItem}s based on the tags on the {@link ZabbixDataItem}
     */
    public static class TagsItemBuilder {
        public ZabbixDiscoveryItem build(String key, Collection<ZabbixDataItem> items) {
            final ZabbixDiscoveryItem.Builder builder = ZabbixDiscoveryItem.builder().key(key);
            for (ZabbixDataItem item : items) {
                builder.addValue(
                        new ZabbixDiscoveryItemConfiguration(StreamSupport.stream(item.getTags().spliterator(), false)
                                .collect(Collectors.toMap(tag -> tag.getKey(), tag -> tag.getValue()))));
            }
            return builder.build();
        }
    }


    public static class ZabbixDiscoveryItemConfiguration {
        private Map<String, String> configuration = new HashMap<>();

        public ZabbixDiscoveryItemConfiguration() {
        }

        public ZabbixDiscoveryItemConfiguration(final Map<String, String> configuration) {
            this.configuration.putAll(configuration);
        }

        public ZabbixDiscoveryItemConfiguration put(final String key, final String value) {
            configuration.put(key, value);
            return this;
        }

        public Stream<Map.Entry<String, String>> stream() {
            return configuration.entrySet().stream();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            final ZabbixDiscoveryItemConfiguration that = (ZabbixDiscoveryItemConfiguration) o;
            return Objects.equals(configuration, that.configuration);
        }

        @Override
        public int hashCode() {
            return Objects.hash(configuration);
        }

        @Override
        public String toString() {
            return "ZabbixDiscoveryItemConfiguration{" + "configuration=" + configuration + '}';
        }

    }

}
