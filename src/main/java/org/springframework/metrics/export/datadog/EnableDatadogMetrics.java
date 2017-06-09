package org.springframework.metrics.export.datadog;

import org.springframework.context.annotation.Import;
import org.springframework.metrics.boot.EnableMetrics;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableMetrics
@Import(DatadogMetricsConfiguration.class)
public @interface EnableDatadogMetrics {
}
