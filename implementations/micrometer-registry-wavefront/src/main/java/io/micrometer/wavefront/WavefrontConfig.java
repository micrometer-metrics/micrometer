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
package io.micrometer.wavefront;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

/**
 * Configuration for {@link WavefrontMeterRegistry}.
 *
 * @author Howard Yoo
 * @author Jon Schneider
 */
public interface WavefrontConfig extends StepRegistryConfig {
    /**
     * Publishes to a wavefront sidecar running out of process.
     */
    WavefrontConfig DEFAULT_PROXY = new WavefrontConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String uri() {
            String v = get(prefix() + ".uri");
            return v == null ? "proxy://localhost:2878" : v;
        }
    };

    /**
     * Publishes directly to the Wavefront API, not passing through a sidecar.
     */
    WavefrontConfig DEFAULT_DIRECT = new WavefrontConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String uri() {
            String v = get(prefix() + ".uri");
            return v == null ? "https://longboard.wavefront.com" : v;
        }
    };

    @Override
    default Duration step() {
        String v = get(prefix() + ".step");
        return v == null ? Duration.ofSeconds(10) : Duration.parse(v);
    }

    @Override
    default String prefix() {
        return "wavefront";
    }

    /**
     * @return The URI to publish metrics to. The URI could represent a Wavefront sidecar or the
     * Wavefront API host. This host could also represent an internal proxy set up in your environment
     * that forwards metrics data to the Wavefront API host.
     * <p>If publishing metrics to a Wavefront proxy (as described in https://docs.wavefront.com/proxies_installing.html),
     * the host must be in the proxy://HOST:PORT format.
     */
    default String uri() {
        String v = get(prefix() + ".uri");
        if (v == null)
            throw new MissingRequiredConfigurationException("A uri is required to publish metrics to Wavefront");
        return v;
    }

    /**
     * @return Unique identifier for the app instance that is publishing metrics to Wavefront. Defaults to the local host name.
     */
    default String source() {
        String v = get(prefix() + ".source");
        if (v != null)
            return v;

        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException uhe) {
            return "unknown";
        }
    }

    /**
     * Required when publishing directly to the Wavefront API host, otherwise does nothing.
     *
     * @return The Wavefront API token.
     */
    @Nullable
    default String apiToken() {
        String v = get(prefix() + ".apiToken");
        return v == null ? null : v.trim().length() > 0 ? v : null;
    }

    /**
     * Wavefront metrics are grouped hierarchically by name in the UI. Setting a global prefix separates
     * metrics originating from this app's whitebox instrumentation from those originating from other Wavefront
     * integrations.
     *
     * @return A prefix to add to every metric.
     */
    @Nullable
    default String globalPrefix() {
        return get(prefix() + ".globalPrefix");
    }
}
