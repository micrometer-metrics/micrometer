/**
 * Copyright 2018 Pivotal Software, Inc.
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

import io.micrometer.core.instrument.distribution.HistogramFlavor;
import io.micrometer.core.instrument.push.PushRegistryConfig;
import io.micrometer.core.lang.Nullable;

/**
 * Configuration for {@link OpenTSDBMeterRegistry}.
 *
 * @author Nikolay Ustinov
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
     * attempt to present credentials to OpenTSDBDB.
     */
    @Nullable
    default String userName() {
        return get(prefix() + ".userName");
    }

    /**
     * @return Authenticate requests with this password. By default is {@code null}, and the registry will not
     * attempt to present credentials to OpenTSDBDB.
     */
    @Nullable
    default String password() {
        return get(prefix() + ".password");
    }

    /**
     * Histogram type for backing DistributionSummary && Timer
     *
     * @return Choose which type of histogram to use
     */
    default HistogramFlavor histogramFlavor() {
        String v = get(prefix() + ".histogramFlavor");

        // Default micrometer histogram implementation
        if (v == null)
            return HistogramFlavor.Plain;

        for (HistogramFlavor flavor : HistogramFlavor.values()) {
            if (flavor.toString().equalsIgnoreCase(v))
                return flavor;
        }

        throw new IllegalArgumentException("Unrecognized histogram flavor '" + v + "' (check property " + prefix() + ".histogramFlavor)");
    }
}
