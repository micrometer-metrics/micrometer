/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.graphite;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;

import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

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
     * @return Whether Graphite tags should be used, as opposed to a hierarchical naming
     * convention. Defaults to true if no values are present for
     * {@link GraphiteConfig#tagsAsPrefix}.
     * @see <a href="https://graphite.readthedocs.io/en/latest/tags.html">Graphite Tag
     * Support</a>
     * @since 1.4.0
     */
    default boolean graphiteTagsEnabled() {
        return getBoolean(this, "graphiteTagsEnabled").orElse(tagsAsPrefix().length == 0);
    }

    /**
     * @return For the hierarchical naming convention, turn the specified tag keys into
     * part of the metric prefix. Ignored if {@link GraphiteConfig#graphiteTagsEnabled()}
     * is {@code true}.
     */
    default String[] tagsAsPrefix() {
        return new String[0];
    }

    default TimeUnit rateUnits() {
        return getTimeUnit(this, "rateUnits").orElse(TimeUnit.SECONDS);
    }

    default TimeUnit durationUnits() {
        return getTimeUnit(this, "durationUnits").orElse(TimeUnit.MILLISECONDS);
    }

    default String host() {
        return getString(this, "host").orElse("localhost");
    }

    default int port() {
        return getInteger(this, "port").orElse(2004);
    }

    /**
     * @return {@code true} if publishing is enabled. Default is {@code true}.
     */
    default boolean enabled() {
        return getBoolean(this, "enabled").orElse(true);
    }

    /**
     * @return Protocol to use while shipping data to Graphite.
     */
    default GraphiteProtocol protocol() {
        return getEnum(this, GraphiteProtocol.class, "protocol").orElse(GraphiteProtocol.PICKLED);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> DropwizardConfig.validate(c), checkRequired("rateUnits", GraphiteConfig::rateUnits),
                checkRequired("durationUnits", GraphiteConfig::durationUnits),
                checkRequired("host", GraphiteConfig::host), check("port", GraphiteConfig::port),
                checkRequired("protocol", GraphiteConfig::protocol));
    }

}
