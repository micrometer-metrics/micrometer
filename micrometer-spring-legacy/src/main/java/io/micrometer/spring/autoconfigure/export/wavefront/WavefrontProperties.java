package io.micrometer.spring.autoconfigure.export.wavefront;

import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Wavefront metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties("management.metrics.export.wavefront")
public class WavefrontProperties extends StepRegistryProperties {
    private String host;
    private String port;
    private String source;
    private Boolean enableHistograms;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getEnableHistograms() {
        return enableHistograms;
    }

    public void setEnableHistograms(Boolean enableHistograms) {
        this.enableHistograms = enableHistograms;
    }
}
