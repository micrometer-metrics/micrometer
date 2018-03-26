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

}
