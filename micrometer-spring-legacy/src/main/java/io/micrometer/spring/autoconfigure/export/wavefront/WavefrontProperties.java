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

import java.net.URI;
import java.time.Duration;

import io.micrometer.spring.autoconfigure.export.properties.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Wavefront metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties("management.metrics.export.wavefront")
public class WavefrontProperties extends StepRegistryProperties {

    /**
     * Step size (i.e. reporting frequency) to use.
     */
    private Duration step = Duration.ofSeconds(10);

    /**
     * {@code true} to publish to the Wavefront proxy,
     * {@code false} to publish directly to the Wavefront API.
     */
    private Boolean sendToProxy;

    /**
     * Required when publishing to the Wavefront proxy.
     */
    private ProxyProperties proxy;

    /**
     * Required when publishing directly the Wavefront API.
     */
    private DirectIngestionProperties directIngestion;

    /**
     * At least one of reportMinuteDistribution, reportHourDistribution, and reportDayDistribution
     * must be {@code true} for WavefrontHistograms to be published to Wavefront.
     */
    private WavefrontHistogramProperties wavefrontHistogram;

    /**
     * Uniquely identifies the app instance that is publishing metrics to Wavefront.
     * Defaults to the local host name.
     */
    private String source;

    /**
     * Global prefix to separate metrics originating from this app's white box
     * instrumentation from those originating from other Wavefront integrations when
     * viewed in the Wavefront UI.
     */
    private String globalPrefix;

    /**
     * Interval at which to flush points to Wavefront, in seconds.
     */
    private Integer flushIntervalSeconds;

    public Boolean getSendToProxy() { return sendToProxy; }

    public void setSendToProxy(Boolean sendToProxy) { this.sendToProxy = sendToProxy; }

    public ProxyProperties getProxy() { return proxy; }

    public void setProxy(ProxyProperties proxy) { this.proxy = proxy; }

    public DirectIngestionProperties getDirectIngestion() { return directIngestion; }

    public void setDirectIngestion(DirectIngestionProperties directIngestion) {
        this.directIngestion = directIngestion;
    }

    public WavefrontHistogramProperties getWavefrontHistogram() { return wavefrontHistogram; }

    public void setWavefrontHistogram(WavefrontHistogramProperties wavefrontHistogram) {
        this.wavefrontHistogram = wavefrontHistogram;
    }

    @Override
    public Duration getStep() {
        return this.step;
    }

    @Override
    public void setStep(Duration step) {
        this.step = step;
    }

    public String getSource() {
        return this.source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getGlobalPrefix() {
        return this.globalPrefix;
    }

    public void setGlobalPrefix(String globalPrefix) {
        this.globalPrefix = globalPrefix;
    }

    public Integer getFlushIntervalSeconds() { return flushIntervalSeconds; }

    public void setFlushIntervalSeconds(Integer flushIntervalSeconds) {
        this.flushIntervalSeconds = flushIntervalSeconds;
    }

    public static class ProxyProperties {
        /**
         * Required when publishing to the Wavefront proxy.
         *
         * Host name of the Wavefront proxy to publish to.
         */
        private String hostName;

        /**
         * Required when publishing metrics to the Wavefront proxy.
         *
         * Port on which the Wavefront proxy is listening to metrics.
         */
        private Integer metricsPort;

        /**
         * Required when publishing WavefrontHistograms to the Wavefront proxy.
         *
         * Port on which the Wavefront proxy is listening to WavefrontHistogram distributions.
         */
        private Integer distributionPort;

        public String getHostName() {
            return hostName;
        }

        public void setHostName(String hostName) {
            this.hostName = hostName;
        }

        public Integer getMetricsPort() {
            return metricsPort;
        }

        public void setMetricsPort(Integer metricsPort) {
            this.metricsPort = metricsPort;
        }

        public Integer getDistributionPort() {
            return distributionPort;
        }

        public void setDistributionPort(Integer distributionPort) {
            this.distributionPort = distributionPort;
        }
    }

    public static class DirectIngestionProperties {
        /**
         * Required when publishing directly to the Wavefront API host.
         *
         * URI of the Wavefront API host to publish to.
         */
        private String uri;

        /**
         * Required when publishing directly to the Wavefront API host.
         *
         * The Wavefront API token.
         */
        private String apiToken;

        /**
         * Max queue size of the in-memory buffer when publishing directly to the Wavefront API host.
         */
        private Integer maxQueueSize;

        /**
         * Size of the batch to be reported during every flush when publishing directly to the Wavefront API host.
         */
        private Integer batchSize;

        public String getUri() { return uri; }

        public void setUri(String uri) { this.uri = uri; }

        public String getApiToken() { return apiToken; }

        public void setApiToken(String apiToken) { this.apiToken = apiToken; }

        public Integer getMaxQueueSize() { return maxQueueSize; }

        public void setMaxQueueSize(Integer maxQueueSize) { this.maxQueueSize = maxQueueSize; }

        public Integer getBatchSize() { return batchSize; }

        public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }
    }

    public static class WavefrontHistogramProperties {
        /**
         * {@code true} to report WavefrontHistogram distributions aggregated into minute intervals.
         */
        private Boolean reportMinuteDistribution;

        /**
         * {@code true} to report WavefrontHistogram distributions aggregated into hour intervals.
         */
        private Boolean reportHourDistribution;

        /**
         * {@code true} to report WavefrontHistogram distributions aggregated into day intervals.
         */
        private Boolean reportDayDistribution;

        public Boolean getReportMinuteDistribution() { return reportMinuteDistribution; }

        public void setReportMinuteDistribution(Boolean reportMinuteDistribution) {
            this.reportMinuteDistribution = reportMinuteDistribution;
        }

        public Boolean getReportHourDistribution() { return reportHourDistribution; }

        public void setReportHourDistribution(Boolean reportHourDistribution) {
            this.reportHourDistribution = reportHourDistribution;
        }

        public Boolean getReportDayDistribution() { return reportDayDistribution; }

        public void setReportDayDistribution(Boolean reportDayDistribution) {
            this.reportDayDistribution = reportDayDistribution;
        }
    }
}
