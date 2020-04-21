/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.graphite;

import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.lang.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for {@link GraphiteMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface GraphiteConfig extends DropwizardConfig {
    /**
     * Accept configuration defaults
     */
    GraphiteConfig DEFAULT = k -> null;

    /**
     * Get the value associated with a key.
     *
     * @param key Key to lookup in the config.
     * @return Value for the key or null if no key is present.
     */
    @Nullable
    String get(String key);

    /**
     * @return Property prefix to prepend to configuration names.
     */
    default String prefix() {
        return "graphite";
    }

    /**
     * @return Whether Graphite tags should be used, as opposed to a hierarchical naming convention.
     * Defaults to true if no values are present for {@link GraphiteConfig#tagsAsPrefix}.
     * @see <a href="https://graphite.readthedocs.io/en/latest/tags.html">Graphite Tag Support</a>
     * @since 1.4.0
     */
    default boolean graphiteTagsEnabled() {
        String v = get(prefix() + ".graphiteTagsEnabled");
        return v == null ? tagsAsPrefix().length == 0 : Boolean.parseBoolean(v);
    }

    /**
     * @return For the hierarchical naming convention, turn the specified tag keys into
     * part of the metric prefix. Ignored if {@link GraphiteConfig#graphiteTagsEnabled()} is {@code true}.
     */
    default String[] tagsAsPrefix() {
        return new String[0];
    }

    default TimeUnit rateUnits() {
        String v = get(prefix() + ".rateUnits");
        return v == null ? TimeUnit.SECONDS : TimeUnit.valueOf(v.toUpperCase());
    }

    default TimeUnit durationUnits() {
        String v = get(prefix() + ".durationUnits");
        return v == null ? TimeUnit.MILLISECONDS : TimeUnit.valueOf(v.toUpperCase());
    }

    default String host() {
        String v = get(prefix() + ".host");
        return (v == null) ? "localhost" : v;
    }

    default int port() {
        String v = get(prefix() + ".port");
        return (v == null) ? 2004 : Integer.parseInt(v);
    }

    /**
     * @return {@code true} if publishing is enabled. Default is {@code true}.
     */
    default boolean enabled() {
        String v = get(prefix() + ".enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * @return Protocol to use while shipping data to Graphite.
     */
    default GraphiteProtocol protocol() {
        String v = get(prefix() + ".protocol");

        if (v == null)
            return GraphiteProtocol.PICKLED;

        for (GraphiteProtocol flavor : GraphiteProtocol.values()) {
            if (flavor.toString().equalsIgnoreCase(v))
                return flavor;
        }

        throw new IllegalArgumentException("Unrecognized graphite protocol '" + v + "' (check property " + prefix() + ".protocol)");
    }
}
