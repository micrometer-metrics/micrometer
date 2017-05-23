package org.springframework.metrics.export.prometheus;

import org.springframework.context.annotation.Import;
import org.springframework.metrics.boot.EnableMetrics;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableMetrics
@EnablePrometheusScraping
@Import(PrometheusMetricsConfiguration.class)
public @interface EnablePrometheusMetrics {
}
