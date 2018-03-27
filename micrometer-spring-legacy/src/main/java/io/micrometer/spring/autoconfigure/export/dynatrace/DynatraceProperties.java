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
     * URI to ship metrics to. If you need to publish metrics to self hosted instance or
     * an internal proxy en-route to SaaS, you can define the location with this.
     */
    private String uri;

    /**
     * When using Dynatrace as SaaS, specify the tenant to report metrics to
     */
    private String tenant;

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

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
