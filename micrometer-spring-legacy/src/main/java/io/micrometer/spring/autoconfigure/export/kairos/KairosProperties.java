package io.micrometer.spring.autoconfigure.export.kairos;

import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Kairos metrics export.
 *
 * @author Anton Ilinchik
 */
@ConfigurationProperties(prefix = "management.metrics.export.kairos")
public class KairosProperties extends StepRegistryProperties {

    /**
     * The host to send the metrics to
     */
    private String host;

    /**
     * The Basic Authentication username.
     */
    private String userName;

    /**
     * The Basic Authentication password.
     */
    private String password;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
