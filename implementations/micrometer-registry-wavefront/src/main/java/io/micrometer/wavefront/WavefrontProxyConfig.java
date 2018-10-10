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
 * Configuration for {@link WavefrontMeterRegistry} that publishes to a Wavefront proxy.
 *
 * @author Han Zhang
 */
public interface WavefrontProxyConfig extends StepRegistryConfig {
    /**
     * Publishes to a Wavefront proxy.
     *
     * Defaults:
     *  hostName: localhost
     *  metricsPort: 2878
     */
    WavefrontProxyConfig DEFAULT = new WavefrontProxyConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String hostName() {
            String v = get(prefix() + ".hostName");
            return v == null ? "localhost" : v;
        }

        @Override
        public Integer metricsPort() {
            String v = get(prefix() + ".metricsPort");
            return v == null ? 2878 : Integer.parseInt(v);
        }
    };

    @Override
    default Duration step() {
        String v = get(prefix() + ".step");
        return v == null ? Duration.ofSeconds(10) : Duration.parse(v);
    }

    @Override
    default String prefix() {
        return "wavefront.proxy";
    }

    /**
     * Required when publishing to the Wavefront proxy.
     *
     * @return The host name of the Wavefront proxy to publish to.
     */
    default String hostName() {
        String v = get(prefix() + ".hostName");
        if (v == null) {
            throw new MissingRequiredConfigurationException(
                "A host name is required to publish metrics to a Wavefront proxy");
        }
        return v;
    }

    /**
     * Required when publishing metrics to the Wavefront proxy.
     *
     * @return The port on which the Wavefront proxy is listening to metrics.
     */
    @Nullable
    default Integer metricsPort() {
        String v = get(prefix() + ".metricsPort");
        return v == null ? null : Integer.parseInt(v);
    }

    /**
     * @return Unique identifier for the app instance that is publishing metrics to Wavefront.
     *         Defaults to the local host name.
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

    /**
     * @return Interval at which to flush points to Wavefront, in seconds.
     *         If null, points will be flushed at a default interval defined by WavefrontSender.
     */
    @Nullable
    default Integer flushIntervalSeconds() {
        String v = get(prefix() + ".flushIntervalSeconds");
        return v == null ? null : Integer.parseInt(v);
    }
}
