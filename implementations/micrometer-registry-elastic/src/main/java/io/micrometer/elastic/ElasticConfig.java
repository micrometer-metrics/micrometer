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
package io.micrometer.elastic;

import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.util.concurrent.TimeUnit;

/**
 * @author Nicolas Portmann
 */
public interface ElasticConfig extends StepRegistryConfig {

    /**
     * Accept configuration defaults
     */
    ElasticConfig DEFAULT = k -> null;

    /**
     * Get the value associated with a key.
     *
     * @param key
     *     Key to lookup in the config.
     * @return
     *     Value for the key or null if no key is present.
     */
    String get(String key);

    /**
     * Property prefix to prepend to configuration names.
     */
    default String prefix() {
        return "elastic";
    }

    /**
     * The hosts to send the metrics to
     * Default is "http://localhost:9200"
     */
    default String[] hosts() {
        String v = get(prefix() + ".hosts");
        return v == null ? new String[]{"http://localhost:9200"} : v.split(",");
    }

    /**
     * Prefix all metrics with a given {@link String}.
     * Default is ""
     */
    default String metricPrefix() {
        String v = get(prefix() + ".metricPrefix");
        return v == null ? "" : v;
    }

    /**
     * Convert all durations to a certain {@link TimeUnit}
     * Default is {@link TimeUnit#SECONDS}
     */
    default TimeUnit rateUnits() {
        String v = get(prefix() + ".rateUnits");
        return v == null ? TimeUnit.SECONDS : TimeUnit.valueOf(v.toUpperCase());
    }

    /**
     * Convert all durations to a certain {@link TimeUnit}
     * Default is {@link TimeUnit#MILLISECONDS}
     */
    default TimeUnit durationUnits() {
        String v = get(prefix() + ".durationUnits");
        return v == null ? TimeUnit.MILLISECONDS : TimeUnit.valueOf(v.toUpperCase());
    }

    /**
     * The timeout to wait for until a connection attempt is and the next host is tried.
     * Default is: 1000
     */
    default int timeout() {
        String v = get(prefix() + ".timeout");
        return v == null ? 1000 : Integer.parseInt(v);
    }

    /**
     * The index name to write metrics to.
     * Default is: "metrics"
     */
    default String index() {
        String v = get(prefix() + ".index");
        return v == null ? "metrics" : v;
    }

    /**
     * The index date format used for rolling indices.
     * This is appended to the index name, split by a '-'.
     * Default is: "yyyy-MM"
     */
    default String indexDateFormat() {
        String v = get(prefix() + ".indexDateFormat");
        return v == null ? "yyyy-MM" : v;
    }

    /**
     * The bulk size per request.
     * Default is: 2500
     */
    default int bulkSize() {
        String v = get(prefix() + ".bulkSize");
        return v == null ? 2500 : Integer.parseInt(v);
    }

    /**
     * The name of the timestamp field.
     * Default is: "@timestamp"
     */
    default String timeStampFieldName() {
        String v = get(prefix() + ".timeStampFieldName");
        return v == null ? "@timestamp" : v;
    }

    /**
     * Whether to create the index automatically if it doesn't exist.
     * Default is: {@code true}
     */
    default boolean autoCreateIndex() {
        String v = get(prefix() + ".autoCreateIndex");
        return v == null || Boolean.valueOf(v);
    }

    /**
     * The Basic Authentication username.
     * Default is: "" (= do not perform Basic Authentication)
     */
    default String userName() {
        String v = get(prefix() + ".userName");
        return v == null ? "" : v;
    }

    /**
     * The Basic Authentication password.
     * Default is: "" (= do not perform Basic Authentication)
     */
    default String password() {
        String v = get(prefix() + ".password");
        return v == null ? "" : v;
    }
}
