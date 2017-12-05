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
package io.micrometer.spring.autoconfigure.export.statsd;

import io.micrometer.statsd.StatsdFlavor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@link ConfigurationProperties} for configuring Influx metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "spring.metrics.export.statsd")
public class StatsdProperties {
    /**
     * Enable publishing to the backend.
     */
    private Boolean enabled = true;

    /**
     * Choose which variant of the StatsD line protocol to use.
     */
    private StatsdFlavor flavor = StatsdFlavor.Datadog;

    /**
     * The host name of the StatsD agent.
     */
    private String host = "localhost";

    /**
     * The UDP port of the StatsD agent.
     */
    private Integer port = 8125;

    /**
     * The total length of a single payload should be kept within your network's MTU.
     */
    private Integer maxPacketLength = 1400;

    /**
     * Determines how often gauges will be polled. When a gauge is polled, its value is recalculated. If the value has changed,
     * it is sent to the StatsD server.
     */
    private Duration pollingFrequency = Duration.ofSeconds(10);

    /**
     * Governs the maximum size of the queue of items waiting to be sent to a StatsD agent over UDP.
     */
    private Integer queueSize = Integer.MAX_VALUE;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public StatsdFlavor getFlavor() {
        return flavor;
    }

    public void setFlavor(StatsdFlavor flavor) {
        this.flavor = flavor;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Integer getMaxPacketLength() {
        return maxPacketLength;
    }

    public void setMaxPacketLength(Integer maxPacketLength) {
        this.maxPacketLength = maxPacketLength;
    }

    public Duration getPollingFrequency() {
        return pollingFrequency;
    }

    public void setPollingFrequency(Duration pollingFrequency) {
        this.pollingFrequency = pollingFrequency;
    }

    public Integer getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(Integer queueSize) {
        this.queueSize = queueSize;
    }
}
