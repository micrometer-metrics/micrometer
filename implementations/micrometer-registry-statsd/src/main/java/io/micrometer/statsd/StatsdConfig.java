package io.micrometer.statsd;

import io.micrometer.core.instrument.MeterRegistryConfig;
import io.micrometer.core.instrument.stats.hist.HistogramConfig;

import java.time.Duration;

public interface StatsdConfig extends MeterRegistryConfig, HistogramConfig {
    @Override
    default String prefix() {
        return "statsd";
    }

    default StatsdFlavor flavor() {
        String v = get(prefix() + ".flavor");

        if(v == null)
            return StatsdFlavor.Etsy;

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

    default String host() {
        String v = get(prefix() + ".host");
        return (v == null) ? "127.0.0.1" : v;
    }

    default int port() {
        String v = get(prefix() + ".port");
        return (v == null) ? 8125 : Integer.parseInt(v);
    }

    /**
     * Keep the total length of the payload within your network's MTU. There is no single good value to use, but here are some guidelines for common network scenarios:
     *   1. Fast Ethernet (1432) - This is most likely for Intranets.
     *   2. Gigabit Ethernet (8932) - Jumbo frames can make use of this feature much more efficient.
     *   3. Commodity Internet (512) - If you are routing over the internet a value in this range will be reasonable. You might be able to go higher, but you are at the mercy of all the hops in your route.
     */
    default int maxPacketLength() {
        String v = get(prefix() + ".maxPacketLength");
        return (v == null) ? 512 : Integer.parseInt(v);
    }

    /**
     * Determines how often gauges will be polled. When a gauge is polled, its value is recalculated. If the value has changed,
     * it is sent to the statsd server.
     */
    default Duration gaugePollingFrequency() {
        String v = get(prefix() + ".gaugePollingFrequency");
        return v == null ? Duration.ofSeconds(10) : Duration.parse(v);
    }

    default int queueSize() {
        String v = get(prefix() + ".queueSize");
        return v == null ? Integer.MAX_VALUE : Integer.parseInt(v);
    }
}
