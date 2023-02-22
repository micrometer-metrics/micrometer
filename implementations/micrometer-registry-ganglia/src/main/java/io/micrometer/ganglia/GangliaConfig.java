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
package io.micrometer.ganglia;

import info.ganglia.gmetric4j.gmetric.GMetric;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link GangliaMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface GangliaConfig extends StepRegistryConfig {

    /**
     * Accept configuration defaults
     */
    GangliaConfig DEFAULT = k -> null;

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
        return "ganglia";
    }

    /**
     * Rate units.
     * @return rate units
     * @deprecated since 1.5.0
     */
    @Deprecated
    @Nullable
    default TimeUnit rateUnits() {
        return null;
    }

    default TimeUnit durationUnits() {
        return getTimeUnit(this, "durationUnits").orElse(TimeUnit.MILLISECONDS);
    }

    /**
     * Protocol version.
     * @return protocol version
     * @deprecated since 1.5.0
     */
    @Deprecated
    @Nullable
    default String protocolVersion() {
        return null;
    }

    default GMetric.UDPAddressingMode addressingMode() {
        return getEnum(this, GMetric.UDPAddressingMode.class, "addressingMode")
            .orElse(GMetric.UDPAddressingMode.MULTICAST);
    }

    default int ttl() {
        return getInteger(this, "ttl").orElse(1);
    }

    default String host() {
        return getString(this, "host").orElse("localhost");
    }

    default int port() {
        return getInteger(this, "port").orElse(8649);
    }

    /**
     * @return {@code true} if publishing is enabled. Default is {@code true}.
     */
    default boolean enabled() {
        return getBoolean(this, "enabled").orElse(true);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this, c -> StepRegistryConfig.validate(c), checkRequired("host", GangliaConfig::host),
                check("port", GangliaConfig::port), checkRequired("ttl", GangliaConfig::ttl),
                checkRequired("durationUnits", GangliaConfig::durationUnits),
                checkRequired("addressingMode", GangliaConfig::addressingMode));
    }

}
