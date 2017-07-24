package io.micrometer.spring.samples;

import io.micrometer.spring.export.prometheus.EnablePrometheusMetrics;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.micrometer.spring.samples.components")
@EnablePrometheusMetrics
@EnableScheduling
public class PrometheusSample {
    public static void main(String[] args) {
        SpringApplication.run(PrometheusSample.class, args);
    }
}