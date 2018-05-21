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
     * @param key Key to lookup in the config.
     * @return Value for the key or null if no key is present.
     */
    String get(String key);

    /**
     * Property prefix to prepend to configuration names.
     *
     * @return property prefix
     */
    default String prefix() {
        return "elastic";
    }

    /**
     * The hosts to send the metrics to
     * Default is "http://localhost:9200"
     *
     * @return hosts
     */
    default String[] hosts() {
        String v = get(prefix() + ".hosts");
        return v == null ? new String[]{"http://localhost:9200"} : v.split(",");
    }

    /**
     * The index name to write metrics to.
     * Default is: "metrics"
     *
     * @return index name
     */
    default String index() {
        String v = get(prefix() + ".index");
        return v == null ? "metrics" : v;
    }

    /**
     * The index date format used for rolling indices.
     * This is appended to the index name, split by a '-'.
     * Default is: "yyyy-MM"
     *
     * @return date format for index
     */
    default String indexDateFormat() {
        String v = get(prefix() + ".indexDateFormat");
        return v == null ? "yyyy-MM" : v;
    }

    /**
     * The name of the timestamp field.
     * Default is: "@timestamp"
     *
     * @return field name for timestamp
     */
    default String timestampFieldName() {
        String v = get(prefix() + ".timestampFieldName");
        return v == null ? "@timestamp" : v;
    }

    /**
     * Whether to create the index automatically if it doesn't exist.
     * Default is: {@code true}
     *
     * @return whether to create the index automatically
     */
    default boolean autoCreateIndex() {
        String v = get(prefix() + ".autoCreateIndex");
        return v == null || Boolean.valueOf(v);
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
