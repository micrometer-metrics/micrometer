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
package io.micrometer.statsd;

import io.micrometer.core.instrument.MeterRegistryConfig;
import io.micrometer.core.instrument.stats.hist.HistogramConfig;

import java.time.Duration;

/**
 * @author Jon Schneider
 */
public interface StatsdConfig extends MeterRegistryConfig, HistogramConfig {
    /**
     * Accept configuration defaults
     */
    StatsdConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "statsd";
    }

    /**
     * Choose which variant of the StatsD line protocol to use.
     */
    default StatsdFlavor flavor() {
        String v = get(prefix() + ".flavor");

        // Datadog is the default because it is more frequently requested than
        // vanilla StatsD (Etsy), and Telegraf supports Datadog's format with a configuration
        // option.
        if(v == null)
            return StatsdFlavor.Datadog;

        for (StatsdFlavor flavor : StatsdFlavor.values()) {
            if(flavor.toString().equalsIgnoreCase(v))
                return flavor;
        }

        throw new IllegalArgumentException("Unrecognized statsd flavor '" + v + "' (check property " + prefix() + ".flavor)");
    }

    /**
     * Returns true if publishing is enabled. Default is {@code true}.
     */
    default boolean enabled() {
        String v = get(prefix() + ".enabled");
        return v == null || Boolean.valueOf(v);
    }

    /**
     * The host name of the StatsD agent.
     */
    default String host() {
        String v = get(prefix() + ".host");
        return (v == null) ? "localhost" : v;
    }

    /**
     * The UDP port of the StatsD agent.
     */
    default int port() {
        String v = get(prefix() + ".port");
        return (v == null) ? 8125 : Integer.parseInt(v);
    }

    /**
     * Keep the total length of the payload within your network's MTU. There is no single good value to use, but here are some guidelines for common network scenarios:
     *   1. Fast Ethernet (1432) - This is most likely for Intranets.
     *   2. Gigabit Ethernet (8932) - Jumbo frames can make use of this feature much more efficient.
     *   3. Commodity Internet (512) - If you are routing over the internet a value in this range will be reasonable. You might be able to go higher, but you are at the mercy of all the hops in your route.
     *
     * FIXME implement packet-limiting the StatsD publisher
     */
    default int maxPacketLength() {
        String v = get(prefix() + ".maxPacketLength");

        // 1400 is the value that Datadog has chosen in their client. Seems to work well
        // for most cases.
        return (v == null) ? 1400 : Integer.parseInt(v);
    }

    /**
     * Determines how often gauges will be polled. When a gauge is polled, its value is recalculated. If the value has changed,
     * it is sent to the StatsD server.
     */
    default Duration pollingFrequency() {
        String v = get(prefix() + ".pollingFrequency");
        return v == null ? Duration.ofSeconds(10) : Duration.parse(v);
    }

    /**
     * Governs the maximum size of the queue of items waiting to be sent to a StatsD agent over UDP.
     */
    default int queueSize() {
        String v = get(prefix() + ".queueSize");
        return v == null ? Integer.MAX_VALUE : Integer.parseInt(v);
    }
}
