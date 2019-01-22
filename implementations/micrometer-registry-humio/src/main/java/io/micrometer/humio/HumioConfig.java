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

import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration for {@link HumioMeterRegistry}.
 *
 * @author Martin Westergaard Lassen
 * @since 1.1.0
 */
public interface HumioConfig extends StepRegistryConfig {

    HumioConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "humio";
    }

    /**
     * @return The URI to ship metrics to. If you need to publish metrics to an internal proxy en route to
     * Humio, you can define the location of the proxy with this.
     */
    default String uri() {
        String v = get(prefix() + ".uri");
        return v == null ? "https://cloud.humio.com" : v;
    }

    /**
     * @return The repository name to write metrics to.
     */
    default String repository() {
        String v = get(prefix() + ".repository");
        return v == null ? "sandbox" : v;
    }

    /**
     * Humio uses a concept called "tags" to decide which datasource to store metrics in. This concept
     * is distinct from Micrometer's notion of tags, which divides a metric along dimensional boundaries.
     * All metrics from this registry will be stored under a datasource defined by these tags.
     *
     * @return Tags which uniquely determine the datasource to store metrics in.
     */
    @Nullable
    default Map<String, String> tags() {
        return null;
    }

    @Nullable
    default String apiToken() {
        return get(prefix() + ".apiToken");
    }

    @Deprecated
    @Override
    default Duration connectTimeout() {
        String v = get(prefix() + ".connectTimeout");
        // Humio regularly times out when the default is 1 second.
        return v == null ? Duration.ofSeconds(5) : Duration.parse(v);
    }
}
