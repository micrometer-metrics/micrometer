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

import io.github.hengyunabc.zabbix.sender.DataObject;
import io.github.hengyunabc.zabbix.sender.SenderResult;
import io.github.hengyunabc.zabbix.sender.ZabbixSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;

/**
 * Publishing discovery items to Zabbix.
 *
 * @see <a href="https://www.zabbix.com/documentation/current/manual/discovery/low_level_discovery">Zabbix low level discovery</a>
 */
public class ZabbixDiscoveryPublisher {

    private final Logger logger = LoggerFactory.getLogger(ZabbixDiscoveryPublisher.class);

    private final ZabbixSender zabbixSender;
    private final ZabbixConfig config;

    /**
     * Cache for the already sent items.
     */
    private final Map<String, ZabbixDiscoveryItem> sentItems;

    public ZabbixDiscoveryPublisher(final ZabbixSender zabbixSender, final ZabbixConfig config) {
        this.zabbixSender = zabbixSender;
        this.config = config;
        sentItems = new HashMap<>();
    }

    /**
     * Build a discovery announcement string to be sent to Zabbix.
     *
     * @param values the values that are included in the announcement string
     * @return a String in the form {"data": [{"#{KEY1}": "value1a", "#{KEY2}": "value2a"}, {"#{KEY1}": "value1b",
     * "#{KEY2}": "value2b"}]}
     */
    public static String buildMetricAnnouncement(final Set<ZabbixDiscoveryItem.ZabbixDiscoveryItemConfiguration> values) {
        return String.format("{\"data\":[%s]}",
                values.stream()
                        .map(i -> "{" + i.stream()
                                .map(e -> String.format("\"{#%s}\":\"%s\"", escapeJson(e.getKey().toUpperCase()), escapeJson(e.getValue())))
                                .collect(Collectors.joining(", ")) + "}")
                        .collect(Collectors.joining(", ")));
    }

    /**
     * Publish the provided items to Zabbix.
     *
     * @param items The items that should be published.
     */
    public void publish(Collection<ZabbixDiscoveryItem> items) {
        if (items.isEmpty()) {
            return;
        }
        logger.trace("discovery items: {}", items);

        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final OffsetDateTime updateWaitingDelay = now.with(t -> t.minus(config.discoveryDelay()));

        try {
            final List<ZabbixDiscoveryItem> eligibleDiscoveryItems = items.stream()
                    .filter(item -> !sentItems.containsKey(item.getKey()) // new item
                            || !sentItems.get(item.getKey()).getValues().equals(item.getValues()) // existing item with new values
                            || !sentItems.get(item.getKey()).getLastSent().isAfter(updateWaitingDelay) // item that wasn't sent for a defined time
                    )
                    .collect(Collectors.toList());

            if (eligibleDiscoveryItems.isEmpty()) {
                logger.debug("no eligible discovery items to send");
                return;
            }

            final List<DataObject> requestBody = eligibleDiscoveryItems.stream()
                    .map(item -> DataObject.builder()
                            .host(config.host())
                            .key(item.getKey())
                            .value(buildMetricAnnouncement(item.getValues()))
                            .build())
                    .collect(Collectors.toList());
            SenderResult senderResult = zabbixSender.send(requestBody);

            logger.trace("discovery payload: {}", requestBody);
            if (!senderResult.success()) {
                logger.debug("failed discovery payload: {}", requestBody);
                logger.error("failed to send discovery to Zabbix @ {}:{} (sent {} items but created {} items): {}",
                        config.instanceHost(), config.instancePort(), senderResult.getTotal(), senderResult.getProcessed(),
                        senderResult);
            } else {
                eligibleDiscoveryItems.forEach(item -> {
                    item.setLastSent(now);
                    sentItems.put(item.getKey(), item);
                });
                logger.debug("successfully sent {} discovery items to Zabbix", senderResult.getTotal());
            }
        } catch (Throwable e) {
            logger.error("failed to send item discovery to Zabbix", e);
        }
    }

}
