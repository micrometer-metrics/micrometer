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

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ZabbixDiscoveryPublisherTest {

    @Test
    void buildEmptyMetricAnnouncement() {
        final String metricAnnouncement = ZabbixDiscoveryPublisher.buildMetricAnnouncement(Collections.emptySet());
        assertThat(metricAnnouncement).isEqualTo("{\"data\":[]}");
    }

    @Test
    void buildSingleConfigurationMetricAnnouncement() {
        final String metricAnnouncement = ZabbixDiscoveryPublisher.buildMetricAnnouncement(
                Collections.singleton(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration(
                        Collections.singletonMap("key", "value"))));
        assertThat(metricAnnouncement).isEqualTo("{\"data\":[{\"{#KEY}\":\"value\"}]}");
    }

    @Test
    void buildMultipleValuesSingleConfigurationMetricAnnouncement() {
        final String metricAnnouncement = ZabbixDiscoveryPublisher.buildMetricAnnouncement(
                Collections.singleton(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration()
                        .put("key1", "value1").put("key2", "value2")));
        assertThat(metricAnnouncement).isEqualTo("{\"data\":[{\"{#KEY1}\":\"value1\", \"{#KEY2}\":\"value2\"}]}");
    }

    @Test
    void buildMultipleValuesMultipleConfigurationMetricAnnouncement() {
        final String metricAnnouncement = ZabbixDiscoveryPublisher.buildMetricAnnouncement(
                Stream.of(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration()
                        .put("key1", "value1a").put("key2", "value2a"), new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration()
                        .put("key1", "value1b").put("key2", "value2b")).collect(Collectors.toCollection(HashSet::new)));
        assertThat(metricAnnouncement).isEqualTo("{\"data\":[{\"{#KEY1}\":\"value1a\", \"{#KEY2}\":\"value2a\"}, {\"{#KEY1}\":\"value1b\", \"{#KEY2}\":\"value2b\"}]}");
    }

    @Test
    void buildSingleConfigurationWithJsonEscapeCharactersMetricAnnouncement() {
        final String metricAnnouncement = ZabbixDiscoveryPublisher.buildMetricAnnouncement(
                Collections.singleton(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration(
                        Collections.singletonMap("key", "v\"alu\"e"))));
        assertThat(metricAnnouncement).isEqualTo("{\"data\":[{\"{#KEY}\":\"v\\\"alu\\\"e\"}]}");
    }

}
