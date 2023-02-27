/*
 * Copyright 2023 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    void testCoreResolves() {
        Bundle bundle = context.getBundle();
        assertThat(bundle).isNotNull();

        CompositeMeterRegistry registry = Metrics.globalRegistry;
        assertThat(registry).isNotNull();
    }

    @Test
    void testPrometheusMeterRegistryResolves() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        assertThat(registry).isNotNull();
        testMetrics(registry);
        logger.info(registry.scrape());
    }

    @Test
    void testJmxMeterRegistryResolves() {
        JmxMeterRegistry registry = new JmxMeterRegistry(JmxConfig.DEFAULT, clock);
        assertThat(registry).isNotNull();
        testMetrics(registry);
    }

    private void testMetrics(MeterRegistry registry) {
        registry.counter("micrometer.test.counter").increment();
        registry.timer("micrometer.test.timer").record(Duration.ofMillis(123));
    }

}
