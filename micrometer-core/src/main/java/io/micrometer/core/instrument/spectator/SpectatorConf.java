package io.micrometer.core.instrument.spectator;

import com.netflix.spectator.api.RegistryConfig;

import java.time.Duration;

public interface SpectatorConf extends RegistryConfig {
    /**
     * Property prefix to prepend to configuration names.
     */
    String prefix();

    /**
     * A bucket filter clamping the bucket domain of timer percentiles histograms to some max value.
     * This is used to limit the number of buckets shipped to save on storage.
     */
    default Duration timerPercentilesMax() {
        String v = get(prefix() + ".timerPercentilesMax");
        return v == null ? Duration.ofMinutes(2) : Duration.parse(v);
    }

    /**
     * A bucket filter clamping the bucket domain of timer percentiles histograms to some min value.
     * This is used to limit the number of buckets shipped to save on storage.
     */
    default Duration timerPercentilesMin() {
        String v = get(prefix() + ".timerPercentilesMin");
        return v == null ? Duration.ofMillis(10) : Duration.parse(v);
    }
}
