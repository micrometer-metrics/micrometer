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
package io.micrometer.ganglia;

import info.ganglia.gmetric4j.gmetric.GMetric;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.lang.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for {@link GangliaMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface GangliaConfig extends DropwizardConfig {
    /**
     * Accept configuration defaults
     */
    GangliaConfig DEFAULT = k -> null;

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
        return "ganglia";
    }

    default TimeUnit rateUnits() {
        String v = get(prefix() + ".rateUnits");
        return v == null ? TimeUnit.SECONDS : TimeUnit.valueOf(v.toUpperCase());
    }

    default TimeUnit durationUnits() {
        String v = get(prefix() + ".durationUnits");
        return v == null ? TimeUnit.MILLISECONDS : TimeUnit.valueOf(v.toUpperCase());
    }

    default String protocolVersion() {
        String v = get(prefix() + ".protocolVersion");
        if (v == null)
            return "3.1";
        if (!v.equals("3.1") && !v.equals("3.0")) {
            throw new IllegalArgumentException("Ganglia version must be one of 3.1 or 3.0 (check property " + prefix() + ".protocolVersion)");
        }
        return v;
    }

    default GMetric.UDPAddressingMode addressingMode() {
        String v = get(prefix() + ".addressingMode");
        if (v == null)
            return GMetric.UDPAddressingMode.MULTICAST;
        if (!v.equalsIgnoreCase("unicast") && !v.equalsIgnoreCase("multicast")) {
            throw new IllegalArgumentException("Ganglia UDP addressing mode must be one of 'unicast' or 'multicast' (check property " + prefix() + ".addressingMode)");
        }
        return GMetric.UDPAddressingMode.valueOf(v.toUpperCase());
    }

    default int ttl() {
        String v = get(prefix() + ".ttl");
        return (v == null) ? 1 : Integer.parseInt(v);
    }

    default String host() {
        String v = get(prefix() + ".host");
        return (v == null) ? "localhost" : v;
    }

    default int port() {
        String v = get(prefix() + ".port");
        return (v == null) ? 8649 : Integer.parseInt(v);
    }

    /**
     * @return {@code true} if publishing is enabled. Default is {@code true}.
     */
    default boolean enabled() {
        String v = get(prefix() + ".enabled");
        return v == null || Boolean.valueOf(v);
    }
}
