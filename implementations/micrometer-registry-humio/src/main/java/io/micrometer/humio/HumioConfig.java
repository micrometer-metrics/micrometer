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
package io.micrometer.humio;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;

/**
 * @author Martin Westergaard Lassen
 */
public interface HumioConfig extends StepRegistryConfig {

    /**
     * Accept configuration defaults
     */
    HumioConfig DEFAULT = k -> null;

    /**
     * Get the value associated with a key.
     *
     * @param key Key to lookup in the config.
     * @return Value for the key or null if no key is present.
     */
    String get(String key);

    @Override
    default String prefix() {
        return "humio";
    }

    /**
     * The host to send the metrics to
     * Default is "https://cloud.humio.com"
     *
     * @return host
     */
    default String host() {
        String v = get(prefix() + ".host");
        return v == null ? "https://cloud.humio.com" : v;
    }

    /**
     * The repository name to write metrics to.
     * Default is: "metrics"
     *
     * @return repository name
     */
    default String repository() {
        String v = get(prefix() + ".repository");
        if (v == null) {
            throw new MissingRequiredConfigurationException("repository must be set to report metrics to Humio");
        }
        return v;
    }

    /**
     * The Basic Authentication username.
     * Default is: "" (= do not perform Basic Authentication)
     *
     * @return username for Basic Authentication
     */
    default String apiToken() {
        String v = get(prefix() + ".apiToken");
        return v == null ? "" : v;
    }
}
