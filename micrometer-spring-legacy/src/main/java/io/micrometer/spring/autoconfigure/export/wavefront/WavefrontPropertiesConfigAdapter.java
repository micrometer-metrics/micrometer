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
package io.micrometer.spring.autoconfigure.export.wavefront;

import io.micrometer.spring.autoconfigure.export.PropertiesConfigAdapter;
import io.micrometer.wavefront.WavefrontConfig;

/**
 * Adapter to convert {@link WavefrontProperties} to a {@link WavefrontConfig}.
 *
 * @author Jon Schneider
 */
public class WavefrontPropertiesConfigAdapter
    extends PropertiesConfigAdapter<WavefrontProperties> implements WavefrontConfig {

    private final ProxyConfig proxyConfig;
    private final DirectIngestionConfig directIngestionConfig;
    private final WavefrontHistogramConfig wavefrontHistogramConfig;

    public WavefrontPropertiesConfigAdapter(WavefrontProperties properties) {
        super(properties);
        proxyConfig = new ProxyPropertiesConfigAdapter(properties.getProxy());
        directIngestionConfig =
            new DirectIngestionPropertiesConfigAdapter(properties.getDirectIngestion());
        wavefrontHistogramConfig =
            new WavefrontHistogramPropertiesConfigAdapter(properties.getWavefrontHistogram());
    }

    @Override
    public String get(String k) { return null; }

    @Override
    public boolean sendToProxy() {
        return get(WavefrontProperties::getSendToProxy, WavefrontConfig.DEFAULT_DIRECT::sendToProxy);
    }

    @Override
    public ProxyConfig proxyConfig() {
        return proxyConfig;
    }

    @Override
    public DirectIngestionConfig directIngestionConfig() {
        return directIngestionConfig;
    }

    @Override
    public WavefrontHistogramConfig wavefrontHistogramConfig() { return wavefrontHistogramConfig; }

    @Override
    public String source() {
        return get(WavefrontProperties::getSource, WavefrontConfig.super::source);
    }

    @Override
    public String globalPrefix() {
        return get(WavefrontProperties::getGlobalPrefix, WavefrontConfig.super::globalPrefix);
    }

    @Override
    public Integer flushIntervalSeconds() {
        return get(WavefrontProperties::getFlushIntervalSeconds, WavefrontConfig.super::flushIntervalSeconds);
    }

    public static class ProxyPropertiesConfigAdapter
        extends PropertiesConfigAdapter<WavefrontProperties.ProxyProperties>
        implements WavefrontConfig.ProxyConfig {

        public ProxyPropertiesConfigAdapter(WavefrontProperties.ProxyProperties proxyProperties) {
            super(proxyProperties);
        }

        @Override
        public String get(String k) { return null; }

        @Override
        public String hostName() {
            return get(WavefrontProperties.ProxyProperties::getHostName,
                WavefrontConfig.DEFAULT_PROXY_CONFIG::hostName);
        }

        @Override
        public Integer metricsPort() {
            return get(WavefrontProperties.ProxyProperties::getMetricsPort,
                WavefrontConfig.DEFAULT_PROXY_CONFIG::metricsPort);
        }

        @Override
        public Integer distributionPort() {
            return get(WavefrontProperties.ProxyProperties::getDistributionPort,
                WavefrontConfig.DEFAULT_PROXY_CONFIG::distributionPort);
        }
    }

    public static class DirectIngestionPropertiesConfigAdapter
        extends PropertiesConfigAdapter<WavefrontProperties.DirectIngestionProperties>
        implements WavefrontConfig.DirectIngestionConfig {

        public DirectIngestionPropertiesConfigAdapter(
            WavefrontProperties.DirectIngestionProperties proxyProperties) {
            super(proxyProperties);
        }

        @Override
        public String get(String k) { return null; }

        @Override
        public String uri() {
            return get(WavefrontProperties.DirectIngestionProperties::getUri,
                WavefrontConfig.DEFAULT_DIRECT_CONFIG::uri);
        }

        @Override
        public String apiToken() {
            return get(WavefrontProperties.DirectIngestionProperties::getApiToken,
                WavefrontConfig.DEFAULT_DIRECT_CONFIG::apiToken);
        }

        @Override
        public Integer maxQueueSize() {
            return get(WavefrontProperties.DirectIngestionProperties::getMaxQueueSize,
                WavefrontConfig.DEFAULT_DIRECT_CONFIG::maxQueueSize);
        }

        @Override
        public Integer batchSize() {
            return get(WavefrontProperties.DirectIngestionProperties::getBatchSize,
                WavefrontConfig.DEFAULT_DIRECT_CONFIG::batchSize);
        }
    }

    public static class WavefrontHistogramPropertiesConfigAdapter
        extends PropertiesConfigAdapter<WavefrontProperties.WavefrontHistogramProperties>
        implements WavefrontConfig.WavefrontHistogramConfig {

        public WavefrontHistogramPropertiesConfigAdapter(
            WavefrontProperties.WavefrontHistogramProperties wavefrontHistogramProperties) {
            super(wavefrontHistogramProperties);
        }

        @Override
        public String get(String k) { return null; }

        @Override
        public boolean reportMinuteDistribution() {
            return get(WavefrontProperties.WavefrontHistogramProperties::getReportMinuteDistribution,
                WavefrontConfig.DEFAULT_WAVEFRONT_HISTOGRAM_CONFIG::reportMinuteDistribution);
        }

        @Override
        public boolean reportHourDistribution() {
            return get(WavefrontProperties.WavefrontHistogramProperties::getReportHourDistribution,
                WavefrontConfig.DEFAULT_WAVEFRONT_HISTOGRAM_CONFIG::reportHourDistribution);
        }

        @Override
        public boolean reportDayDistribution() {
            return get(WavefrontProperties.WavefrontHistogramProperties::getReportDayDistribution,
                WavefrontConfig.DEFAULT_WAVEFRONT_HISTOGRAM_CONFIG::reportDayDistribution);
        }
    }
}
