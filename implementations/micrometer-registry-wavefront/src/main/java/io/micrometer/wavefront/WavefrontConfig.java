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
 * Configuration for {@link WavefrontMeterRegistry}.
 *
 * @author Howard Yoo
 * @author Jon Schneider
 */
public interface WavefrontConfig extends StepRegistryConfig {
    /**
     * Publishes to the Wavefront proxy running out of process.
     */
    WavefrontConfig DEFAULT_PROXY = new WavefrontConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public boolean sendToProxy() { return true; }

        @Override
        public ProxyConfig proxyConfig() {
            return DEFAULT_PROXY_CONFIG;
        }
    };

    /**
     * Publishes directly to the Wavefront API, not passing through the proxy.
     */
    WavefrontConfig DEFAULT_DIRECT = new WavefrontConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public boolean sendToProxy() { return false; }

        @Override
        public DirectIngestionConfig directIngestionConfig() {
            return DEFAULT_DIRECT_CONFIG;
        }
    };

    /**
     * The default configuration for the Wavefront proxy component of WavefrontConfig:
     *  hostName: localhost
     *  metricsPort: 2878
     *  distributionPort: 40000
     */
    WavefrontConfig.ProxyConfig DEFAULT_PROXY_CONFIG = new ProxyConfig() {
        @Override
        public String get(String key) { return null; }

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

        @Override
        public Integer distributionPort() {
            String v = get(prefix() + ".distributionPort");
            return v == null ? 40000 : Integer.parseInt(v);
        }
    };

    /**
     * The default configuration for the direct-to-Wavefront component of WavefrontConfig:
     *  uri: https://longboard.wavefront.com
     *  apiToken: null
     *  maxQueueSize: null
     *  batchSize: null
     */
    WavefrontConfig.DirectIngestionConfig DEFAULT_DIRECT_CONFIG = new DirectIngestionConfig() {
        @Override
        public String get(String key) { return null; }

        @Override
        public String uri() {
            String v = get(prefix() + ".uri");
            return v == null ? "https://longboard.wavefront.com" : v;
        }
    };

    /**
     * The default configuration for the WavefrontHistogram component of WavefrontConfig:
     *  reportMinuteDistribution: false
     *  reportHourDistribution: false
     *  reportDayDistribution: false
     */
    WavefrontConfig.WavefrontHistogramConfig DEFAULT_WAVEFRONT_HISTOGRAM_CONFIG =
        new WavefrontHistogramConfig() {
            @Override
            public String get(String key) { return null; }
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
     * @return {@code true} to publish to the Wavefront proxy,
     *         {@code false} to publish directly to the Wavefront API.
     */
    default boolean sendToProxy() {
        String v = get(prefix() + ".sendToProxy");
        return v == null ? false : Boolean.valueOf(v);
    }

    /**
     * Required when publishing to the Wavefront proxy.
     *
     * @return configuration properties specific to publishing to the Wavefront proxy.
     */
    @Nullable
    default ProxyConfig proxyConfig() {
        if (sendToProxy()) {
            throw new MissingRequiredConfigurationException(
                "A proxy config is required to publish metrics to a Wavefront proxy");
        }
        return null;
    }

    /**
     * Required when publishing directly to the Wavefront API.
     *
     * @return configuration properties specific to publishing directly to the Wavefront API.
     */
    @Nullable
    default DirectIngestionConfig directIngestionConfig() {
        if (!sendToProxy()) {
            throw new MissingRequiredConfigurationException(
                "A direct ingestion config is required to publish metrics directly to Wavefront");
        }
        return null;
    }

    /**
     * At least one of reportMinuteDistribution(), reportHourDistribution(), and reportDayDistribution()
     * must return {@code true} for WavefrontHistograms to be published to Wavefront.
     *
     * @return configuration properties specific to publishing WavefrontHistograms.
     */
    default WavefrontHistogramConfig wavefrontHistogramConfig() {
        return DEFAULT_WAVEFRONT_HISTOGRAM_CONFIG;
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

    interface ProxyConfig extends MeterRegistryConfig {
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
         * Required when publishing WavefrontHistograms to the Wavefront proxy.
         *
         * @return The port on which the Wavefront proxy is listening to WavefrontHistogram distributions.
         */
        @Nullable
        default Integer distributionPort() {
            String v = get(prefix() + ".distributionPort");
            return v == null ? null : Integer.parseInt(v);
        }
    }

    interface DirectIngestionConfig extends MeterRegistryConfig {
        @Override
        default String prefix() {
            return "wavefront.directIngestion";
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
        default Integer batchSize() {
            String v = get(prefix() + ".batchSize");
            return v == null ? null : Integer.parseInt(v);
        }
    }

    interface WavefrontHistogramConfig extends MeterRegistryConfig {
        @Override
        default String prefix() {
            return "wavefront.wavefrontHistogram";
        }

        /**
         * @return {@code true} to report WavefrontHistogram distributions aggregated into minute intervals,
         *         {@code false} otherwise.
         */
        default boolean reportMinuteDistribution() {
            String v = get(prefix() + ".reportMinuteDistribution");
            return v == null ? false : Boolean.valueOf(v);
        }

        /**
         * @return {@code true} to report WavefrontHistogram distributions aggregated into hour intervals,
         *         {@code false} otherwise.
         */
        default boolean reportHourDistribution() {
            String v = get(prefix() + ".reportHourDistribution");
            return v == null ? false : Boolean.valueOf(v);
        }

        /**
         * @return {@code true} to report WavefrontHistogram distributions aggregated into day intervals,
         *         {@code false} otherwise.
         */
        default boolean reportDayDistribution() {
            String v = get(prefix() + ".reportDayDistribution");
            return v == null ? false : Boolean.valueOf(v);
        }
    }
}
