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
package io.micrometer.spring.autoconfigure.export.ganglia;

import info.ganglia.gmetric4j.gmetric.GMetric;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * {@link ConfigurationProperties} for configuring Ganglia metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "spring.metrics.ganglia")
public class GangliaProperties {
    /**
     * Enable publishing to the backend.
     */
    private Boolean enabled = true;

    /**
     * The step size (reporting frequency) to use.
     */
    private Duration step = Duration.ofMinutes(1);

    /**
     * The base time unit used to report rates.
     */
    private TimeUnit rateUnits;

    /**
     * The base time unit used to report durations.
     */
    private TimeUnit durationUnits;

    /**
     * Ganglia protocol version. Must be either 3.1 or 3.0.
     */
    private String protocolVersion;

    /**
     * The UDP addressing mode, either unicast or multicast.
     */
    private GMetric.UDPAddressingMode addressingMode;

    /**
     * The TTL for metrics on Ganglia.
     */
    private Integer timeToLive;

    /**
     * Ganglia host used for publishing.
     */
    private String host;

    /**
     * Ganglia port used for publishing.
     */
    private Integer port;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getStep() {
        return step;
    }

    public void setStep(Duration step) {
        this.step = step;
    }

    public TimeUnit getRateUnits() {
        return rateUnits;
    }

    public void setRateUnits(TimeUnit rateUnits) {
        this.rateUnits = rateUnits;
    }

    public TimeUnit getDurationUnits() {
        return durationUnits;
    }

    public void setDurationUnits(TimeUnit durationUnits) {
        this.durationUnits = durationUnits;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public GMetric.UDPAddressingMode getAddressingMode() {
        return addressingMode;
    }

    public void setAddressingMode(GMetric.UDPAddressingMode addressingMode) {
        this.addressingMode = addressingMode;
    }

    public Integer getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(Integer timeToLive) {
        this.timeToLive = timeToLive;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }
}
