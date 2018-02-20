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
package io.micrometer.datadog;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

/**
 * Configuration for {@link DatadogMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface DatadogConfig extends StepRegistryConfig {
    /**
     * Accept configuration defaults
     */
    DatadogConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "datadog";
    }

    default String apiKey() {
        String v = get(prefix() + ".apiKey");
        if (v == null)
            throw new MissingRequiredConfigurationException("apiKey must be set to report metrics to Datadog");
        return v;
    }

    /**
     * @return The Datadog application key. This is only required if you care for metadata like base units, description,
     * and meter type to be published to Datadog.
     */
    @Nullable
    default String applicationKey() {
        return get(prefix() + ".applicationKey");
    }

    /**
     * @return The tag that will be mapped to "host" when shipping metrics to datadog, or {@code null} if
     * host should be omitted on publishing.
     */
    @Nullable
    default String hostTag() {
        String v = get(prefix() + ".hostTag");
        return v == null ? "instance" : v;
    }

    /**
     * @return The URI to ship metrics to. If you need to publish metrics to an internal proxy en route to
     * datadoghq, you can define the location of the proxy with this.
     */
    default String uri() {
        String v = get(prefix() + ".uri");
        return v == null ? "https://app.datadoghq.com" : v;
    }

    /**
     * @return {@code true} if meter descriptions should be sent to Datadog.
     * Turn this off to minimize the amount of data sent on each scrape.
     */
    default boolean descriptions() {
        String v = get(prefix() + ".descriptions");
        return v == null || Boolean.valueOf(v);
    }
}
