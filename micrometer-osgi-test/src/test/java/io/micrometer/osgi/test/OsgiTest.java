package io.micrometer.osgi.test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({ ServiceExtension.class, BundleContextExtension.class })
public class OsgiTest {

    private final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();

    private final Logger logger = LoggerFactory.getLogger(OsgiTest.class);

    private final MockClock clock = new MockClock();

    @Test
    public void testCoreResolves() {
        Bundle bundle = context.getBundle();
        assertThat(bundle).isNotNull();

        CompositeMeterRegistry registry = Metrics.globalRegistry;
        assertThat(registry).isNotNull();
    }

    @Test
    public void testPrometheusMeterRegistryResolves() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        assertThat(registry).isNotNull();
        testMetrics(registry);
        logger.info(registry.scrape());
    }

    @Test
    public void testJmxMeterRegistryResolves() {
        JmxMeterRegistry registry = new JmxMeterRegistry(JmxConfig.DEFAULT, clock);
        assertThat(registry).isNotNull();
        testMetrics(registry);
    }

    private void testMetrics(MeterRegistry registry) {
        registry.counter("micrometer.test.counter").increment();
        registry.timer("micrometer.test.timer").record(Duration.ofMillis(123));
    }

}
