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
