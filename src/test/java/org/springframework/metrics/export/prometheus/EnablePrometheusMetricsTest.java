package org.springframework.metrics.export.prometheus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.TagFormatter;
import org.springframework.metrics.instrument.prometheus.PrometheusMeterRegistry;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class EnablePrometheusMetricsTest {

    @Autowired
    ApplicationContext context;

    @Test
    void tagFormatting() {
        assertThat(context.getBean(TagFormatter.class))
                .isInstanceOf(PrometheusTagFormatter.class);
    }

    @Test
    void meterRegistry() {
        assertThat(context.getBean(MeterRegistry.class))
                .isInstanceOf(PrometheusMeterRegistry.class);
    }

    @SpringBootApplication
    @EnablePrometheusMetrics
    static class PrometheusApp {}
}
