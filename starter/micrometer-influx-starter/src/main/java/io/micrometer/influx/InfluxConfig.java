/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx;

import io.micrometer.core.instrument.spectator.step.StepRegistryConfig;

/**
 * @author Jon Schneider
 */
public interface InfluxConfig extends StepRegistryConfig {
    @Override
    default String prefix() {
        return "influx";
    }

    /**
     * The tag that will be mapped to "host" when shipping metrics to Influx, or {@code null} if
     * host should be omitted on publishing.
     */
    default String db() {
        String v = get(prefix() + ".db");
        return v == null ? "mydb" : v;
    }

    /**
     * Sets the write consistency for the point. The Influx default is 'one'. Must
     * be one of 'any', 'one', 'quorum', or 'all'.
     *
     * Only available for InfluxEnterprise clusters.
     */
    default InfluxConsistency consistency() {
        String v = get(prefix() + ".consistency");
        if(v == null)
            return InfluxConsistency.ONE;
        return InfluxConsistency.valueOf(v.toUpperCase());
    }

    /**
     * Authenticate requests with this user. By default is {@code null}, and the registry will not
     * attempt to present credentials to Influx.
     */
    default String userName() {
        return get(prefix() + ".userName");
    }

    /**
     * Authenticate requests with this password. By default is {@code null}, and the registry will not
     * attempt to present credentials to Influx.
     */
    default String password() {
        return get(prefix() + ".password");
    }

    /**
     * Influx writes to the DEFAULT retention policy if one is not specified.
     */
    default String retentionPolicy() {
        return get(prefix() + ".retentionPolicy");
    }

    /**
     * Returns the URI for the Influx backend. The default is
     * {@code http://localhost:8086/write}.
     */
    default String uri() {
        String v = get(prefix() + ".uri");
        return (v == null) ? "http://localhost:8086/write" : v;
    }

    /**
     * @return {@code true} if metrics publish batches should be GZIP compressed, {@code false} otherwise.
     */
    default boolean compressed() {
        String v = get(prefix() + ".compressed");
        return v == null || Boolean.valueOf(v);
    }
}
