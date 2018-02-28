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
package io.micrometer.prometheus;

import io.micrometer.core.instrument.config.MeterRegistryConfig;

import java.time.Duration;

/**
 * Configuration for {@link PrometheusMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface PrometheusConfig extends MeterRegistryConfig {
    /**
     * Accept configuration defaults
     */
    PrometheusConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "prometheus";
    }

    /**
     * @return {@code true} if meter descriptions should be sent to Prometheus.
     * Turn this off to minimize the amount of data sent on each scrape.
     */
    default boolean descriptions() {
        String v = get(prefix() + ".descriptions");
        return v == null || Boolean.valueOf(v);
    }

    /**
     * @return The step size to use in computing windowed statistics like max. The default is 1 minute.
     * To get the most out of these statistics, align the step interval to be close to your scrape interval.
     */
    default Duration step() {
        String v = get(prefix() + ".step");
        return v == null ? Duration.ofMinutes(1) : Duration.parse(v);
    }
}
