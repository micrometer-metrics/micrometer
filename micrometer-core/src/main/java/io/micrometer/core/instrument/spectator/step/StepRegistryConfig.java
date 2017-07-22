package io.micrometer.core.instrument.spectator.step;

import com.netflix.spectator.api.RegistryConfig;

import java.time.Duration;

/**
 * Common configuration settings for any registry that pushes aggregated
 * metrics on a regular interval.
 *
 * @author Jon Schneider
 */
public interface StepRegistryConfig extends RegistryConfig {
    /**
     * Property prefix to prepend to configuration names.
     */
    String prefix();
    
    /**
     * Returns the step size (reporting frequency) to use. The default is 10 seconds, matching the default
     * for the Datadog agent.
     */
    default Duration step() {
        String v = get(prefix() + ".step");
        return v == null ? Duration.ofSeconds(10) : Duration.parse(v);
    }

    /**
     * Returns true if publishing to Datadog is enabled. Default is {@code true}.
     */
    default boolean enabled() {
        String v = get(prefix() + ".enabled");
        return v == null || Boolean.valueOf(v);
    }

    /**
     * Returns the number of threads to use with the scheduler. The default is
     * 2 threads.
     */
    default int numThreads() {
        String v = get(prefix() + ".numThreads");
        return v == null ? 2 : Integer.parseInt(v);
    }

    /**
     * Returns the connection timeout for requests to the backend. The default is
     * 1 second.
     */
    default Duration connectTimeout() {
        String v = get(prefix() + ".connectTimeout");
        return v == null ? Duration.ofSeconds(1) : Duration.parse(v);
    }

    /**
     * Returns the read timeout for requests to the backend. The default is
     * 10 seconds.
     */
    default Duration readTimeout() {
        String v = get(prefix() + ".readTimeout");
        return v == null ? Duration.ofSeconds(10) : Duration.parse(v);
    }

    /**
     * Returns the number of measurements per request to use for the backend. If more
     * measurements are found, then multiple requests will be made. The default is
     * 10,000.
     */
    default int batchSize() {
        String v = get(prefix() + ".batchSize");
        return v == null ? 10000 : Integer.parseInt(v);
    }
}
