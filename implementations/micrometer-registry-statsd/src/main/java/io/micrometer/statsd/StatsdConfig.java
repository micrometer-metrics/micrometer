/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.statsd;

import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.core.instrument.config.validate.Validated;

import java.time.Duration;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link StatsdMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface StatsdConfig extends MeterRegistryConfig {

    /**
     * Accept configuration defaults
     */
    StatsdConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "statsd";
    }

    /**
     * @return Choose which variant of the StatsD line protocol to use.
     */
    default StatsdFlavor flavor() {
        // Datadog is the default because it is more frequently requested than
        // vanilla StatsD (Etsy), and Telegraf supports Datadog's format with a
        // configuration
        // option.
        return getEnum(this, StatsdFlavor.class, "flavor").orElse(StatsdFlavor.DATADOG);
    }

    /**
     * @return {@code true} if publishing is enabled. Default is {@code true}.
     */
    default boolean enabled() {
        return getBoolean(this, "enabled").orElse(true);
    }

    /**
     * @return Host (or socket in case of Unix domain socket protocol) to receive StatsD
     * metrics.
     */
    default String host() {
        return getString(this, "host").orElse("localhost");
    }

    /**
     * @return The port of the StatsD agent.
     */
    default int port() {
        return getInteger(this, "port").orElse(8125);
    }

    /**
     * @return the protocol of the connection to the agent
     * @since 1.2.0
     */
    default StatsdProtocol protocol() {
        return getEnum(this, StatsdProtocol.class, "protocol").orElse(StatsdProtocol.UDP);
    }

    /**
     * Keep the total length of the payload within your network's MTU. There is no single
     * good value to use, but here are some guidelines for common network scenarios:
     * <ul>
     * <li>Fast Ethernet (1432) - This is most likely for Intranets.</li>
     * <li>Gigabit Ethernet (8932) - Jumbo frames can make use of this feature much more
     * efficient.</li>
     * <li>Commodity Internet (512) - If you are routing over the internet a value in this
     * range will be reasonable. You might be able to go higher, but you are at the mercy
     * of all the hops in your route.</li>
     * </ul>
     * @return The max length of the payload.
     */
    default int maxPacketLength() {
        // 1400 is the value that Datadog has chosen in their client. Seems to work well
        // for most cases.
        return getInteger(this, "maxPacketLength").orElse(1400);
    }

    /**
     * Determines how often gauges will be polled. When a gauge is polled, its value is
     * recalculated. If the value has changed, it is sent to the StatsD server.
     * @return The polling frequency.
     */
    default Duration pollingFrequency() {
        return getDuration(this, "pollingFrequency").orElse(Duration.ofSeconds(10));
    }

    /**
     * Governs the maximum size of the queue of items waiting to be sent to a StatsD agent
     * over UDP.
     * @return Maximum queue size.
     * @deprecated No longer configurable and unbounded queue will be always used instead.
     */
    @Deprecated
    default int queueSize() {
        return getInteger(this, "queueSize").orElse(Integer.MAX_VALUE);
    }

    /**
     * @return The step size to use in computing windowed statistics like max. The default
     * is 1 minute. To get the most out of these statistics, align the step interval to be
     * close to your scrape interval.
     */
    default Duration step() {
        return getDuration(this, "step").orElse(Duration.ofMinutes(1));
    }

    /**
     * @return {@code true} if unchanged meters should be published to the StatsD server.
     * Default is {@code true}.
     */
    default boolean publishUnchangedMeters() {
        return getBoolean(this, "publishUnchangedMeters").orElse(true);
    }

    /**
     * @return {@code true} if measurements should be buffered before sending to the
     * StatsD server. Default is {@code true}. Measurements will be buffered until
     * reaching the max packet length, or until the polling frequency is reached.
     */
    default boolean buffered() {
        return getBoolean(this, "buffered").orElse(true);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, checkRequired("flavor", StatsdConfig::flavor), checkRequired("host", StatsdConfig::host),
                check("port", StatsdConfig::port), checkRequired("protocol", StatsdConfig::protocol),
                checkRequired("pollingFrequency", StatsdConfig::pollingFrequency),
                checkRequired("step", StatsdConfig::step));
    }

}
