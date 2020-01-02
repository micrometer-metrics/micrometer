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

import io.github.hengyunabc.zabbix.sender.SenderResult;
import io.github.hengyunabc.zabbix.sender.ZabbixSender;
import io.micrometer.core.lang.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ZabbixDiscoveryPublisherTest {

    final ZabbixConfig config = new ZabbixConfig() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        @Nullable
        public String get(String key) {
            return null;
        }
    };

    @Test
    void buildEmptyMetricAnnouncement() {
        final String metricAnnouncement = ZabbixDiscoveryPublisher.buildMetricAnnouncement(Collections.emptySet());
        assertThat(metricAnnouncement).isEqualTo("{\"data\":[]}");
    }

    @Test
    void buildSingleConfigurationMetricAnnouncement() {
        final String metricAnnouncement = ZabbixDiscoveryPublisher.buildMetricAnnouncement(
                Collections.singleton(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration(
                        singletonMap("key", "value"))));
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
                        singletonMap("key", "v\"alu\"e"))));
        assertThat(metricAnnouncement).isEqualTo("{\"data\":[{\"{#KEY}\":\"v\\\"alu\\\"e\"}]}");
    }

    @Test
    void newDiscoveryItemShouldBePublished() throws IOException {
        final ZabbixSender zabbixSender = Mockito.mock(ZabbixSender.class);
        final ZabbixDiscoveryPublisher zabbixDiscoveryPublisher = new ZabbixDiscoveryPublisher(zabbixSender, config);
        when(zabbixSender.send(anyList())).thenReturn(new SenderResult());
        zabbixDiscoveryPublisher.publish(Collections.singletonList(ZabbixDiscoveryItem.builder().key("discovery").addValue(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration(
                singletonMap("key", "value"))).build()));
        verify(zabbixSender, times(1)).send(anyList());
    }

    @Test
    void existingUnchangedDiscoveryItemShouldNotBePublished() throws IOException {
        final ZabbixSender zabbixSender = Mockito.mock(ZabbixSender.class);
        final ZabbixDiscoveryPublisher zabbixDiscoveryPublisher = new ZabbixDiscoveryPublisher(zabbixSender, config);
        when(zabbixSender.send(anyList())).thenReturn(new SenderResult());
        zabbixDiscoveryPublisher.publish(Collections.singletonList(ZabbixDiscoveryItem.builder().key("discovery").addValue(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration(
                singletonMap("key", "value"))).build()));
        zabbixDiscoveryPublisher.publish(Collections.singletonList(ZabbixDiscoveryItem.builder().key("discovery").addValue(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration(
                singletonMap("key", "value"))).build()));
        verify(zabbixSender, times(1)).send(anyList());
    }

    @Test
    void changedDiscoveryItemShouldBePublishedAgain() throws IOException {
        final ZabbixSender zabbixSender = Mockito.mock(ZabbixSender.class);
        final ZabbixDiscoveryPublisher zabbixDiscoveryPublisher = new ZabbixDiscoveryPublisher(zabbixSender, config);
        when(zabbixSender.send(anyList())).thenReturn(new SenderResult());
        zabbixDiscoveryPublisher.publish(Collections.singletonList(ZabbixDiscoveryItem.builder().key("discovery").addValue(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration(
                singletonMap("key", "value"))).build()));
        zabbixDiscoveryPublisher.publish(Collections.singletonList(ZabbixDiscoveryItem.builder().key("discovery").addValue(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration(
                singletonMap("key", "value1"))).build()));
        verify(zabbixSender, times(2)).send(anyList());
    }

    @Test
    void unchangedDiscoveryItemShouldBePublishedAgainAfterDiscoveryDelay() throws IOException {
        final ZabbixConfig config = new ZabbixConfig() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }

            @Override
            public Duration discoveryDelay() {
                return Duration.ofMillis(0L);
            }
        };

        final ZabbixSender zabbixSender = Mockito.mock(ZabbixSender.class);
        final ZabbixDiscoveryPublisher zabbixDiscoveryPublisher = new ZabbixDiscoveryPublisher(zabbixSender, config);
        when(zabbixSender.send(anyList())).thenReturn(new SenderResult());
        zabbixDiscoveryPublisher.publish(Collections.singletonList(ZabbixDiscoveryItem.builder().key("discovery").addValue(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration(
                singletonMap("key", "value"))).build()));
        zabbixDiscoveryPublisher.publish(Collections.singletonList(ZabbixDiscoveryItem.builder().key("discovery").addValue(new ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration(
                singletonMap("key", "value"))).build()));
        verify(zabbixSender, times(2)).send(anyList());
    }

}
