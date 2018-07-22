package io.micrometer.kairos;

import io.micrometer.core.instrument.step.StepRegistryConfig;

/**
 * @author Anton Ilinchik
 */
public interface KairosConfig extends StepRegistryConfig {

    /**
     * Accept configuration defaults
     */
    KairosConfig DEFAULT = k -> null;

    /**
     * Property prefix to prepend to configuration names.
     *
     * @return property prefix
     */
    default String prefix() {
        return "kairos";
    }

    /**
     * The host to send the metrics to
     * Default is "http://localhost:8080"
     *
     * @return host
     */
    default String host() {
        String v = get(prefix() + ".host");
        return v == null ? "http://localhost:8080/api/v1/datapoints" : v;
    }

    /**
     * The Basic Authentication username.
     * Default is: "" (= do not perform Basic Authentication)
     *
     * @return username for Basic Authentication
     */
    default String userName() {
        String v = get(prefix() + ".userName");
        return v == null ? "" : v;
    }

    /**
     * The Basic Authentication password.
     * Default is: "" (= do not perform Basic Authentication)
     *
     * @return password for Basic Authentication
     */
    default String password() {
        String v = get(prefix() + ".password");
        return v == null ? "" : v;
    }
}
