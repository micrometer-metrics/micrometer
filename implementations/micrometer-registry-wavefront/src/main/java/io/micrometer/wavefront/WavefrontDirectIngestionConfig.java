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

import io.micrometer.core.instrument.config.MeterRegistryConfig;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

/**
 * Configuration for {@link WavefrontMeterRegistry} that publishes directly to a Wavefront API host.
 *
 * @author Han Zhang
 */
public interface WavefrontDirectIngestionConfig extends StepRegistryConfig {
    /**
     * Publishes directly to a Wavefront API host.
     *
     * Defaults:
     *  uri: https://longboard.wavefront.com
     *  apiToken: null
     *  maxQueueSize: null
     *  flushBatchSize: null
     */
    WavefrontDirectIngestionConfig DEFAULT = new WavefrontDirectIngestionConfig() {
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
        return "wavefront.direct";
    }

    /**
     * Required when publishing directly to the Wavefront API host.
     *
     * @return The URI of the Wavefront API host to publish to.
     */
    default String uri() {
        String v = get(prefix() + ".uri");
        if (v == null) {
            throw new MissingRequiredConfigurationException(
                "A uri is required to publish metrics directly to Wavefront");
        }
        return v;
    }

    /**
     * Required when publishing directly to the Wavefront API host.
     *
     * @return The Wavefront API token.
     */
    @Nullable
    default String apiToken() {
        String v = get(prefix() + ".apiToken");
        return v == null ? null : v.trim().length() > 0 ? v : null;
    }

    /**
     * @return The max queue size of the in-memory buffer
     *         when publishing directly to the Wavefront API host.
     */
    @Nullable
    default Integer maxQueueSize() {
        String v = get(prefix() + ".maxQueueSize");
        return v == null ? null : Integer.parseInt(v);
    }

    /**
     * @return The size of the batch to be reported during every flush
     *         when publishing directly to the Wavefront API host.
     */
    @Nullable
    default Integer flushBatchSize() {
        String v = get(prefix() + ".flushBatchSize");
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
