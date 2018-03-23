package io.micrometer.spring.autoconfigure.export.dynatrace;


import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Dynatrace metrics export.
 *
 * @author Oriol Barcelona
 */
@ConfigurationProperties(prefix = "management.metrics.export.dynatrace")
public class DynatraceProperties  extends StepRegistryProperties {

    /**
     * Dynatrace API token
     */
    private String apiToken;

    /**
     * URI to ship metrics to. Should be used for SaaS, self managed
     * instances or to en-route through an internal proxy
     */
    private String uri;

    /**
     * The custom device used to identify the sender into Dynatrace
     */
    private String deviceId;

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
