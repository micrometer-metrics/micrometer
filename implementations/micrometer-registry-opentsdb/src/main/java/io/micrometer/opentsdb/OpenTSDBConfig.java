/**
 * Copyright 2020 Pivotal Software, Inc.
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
package io.micrometer.opentsdb;

import io.micrometer.core.instrument.push.PushRegistryConfig;
import io.micrometer.core.lang.Nullable;

/**
 * Configuration for {@link OpenTSDBMeterRegistry}.
 *
 * @author Nikolay Ustinov
 * @since 1.4.0
 */
public interface OpenTSDBConfig extends PushRegistryConfig {
    /**
     * Accept configuration defaults
     */
    OpenTSDBConfig DEFAULT = k -> null;

    /**
     * Property prefix to prepend to configuration names.
     *
     * @return property prefix
     */
    default String prefix() {
        return "opentsdb";
    }

    /**
     * The URI to send the metrics to.
     *
     * @return uri
     */
    default String uri() {
        String v = get(prefix() + ".uri");
        return v == null ? "http://localhost:4242/api/put" : v;
    }

    /**
     * @return Authenticate requests with this user. By default is {@code null}, and the registry will not
     * attempt to present credentials to OpenTSDB.
     */
    @Nullable
    default String userName() {
        return get(prefix() + ".userName");
    }

    /**
     * @return Authenticate requests with this password. By default is {@code null}, and the registry will not
     * attempt to present credentials to OpenTSDB.
     */
    @Nullable
    default String password() {
        return get(prefix() + ".password");
    }

    /**
     * OpenTSDB can be used as a metrics sink to different backends. The registry
     * can react to different flavors to ship metrics differently to guarantee the
     * highest fidelity at their ultimate destination.
     *
     * @return A flavor that influences the style of metrics shipped to OpenTSDB.
     */
    @Nullable
    default OpenTSDBFlavor flavor() {
        String v = get(prefix() + ".flavor");

        if (v == null)
            return null;

        for (OpenTSDBFlavor flavor : OpenTSDBFlavor.values()) {
            if (flavor.name().equalsIgnoreCase(v))
                return flavor;
        }

        throw new IllegalArgumentException("Unrecognized flavor '" + v + "' (check property " + prefix() + ".flavor)");
    }
}
