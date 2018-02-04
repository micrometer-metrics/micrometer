/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = CompositeMeterRegistryConfigurationTest.MetricsApp.class)
@TestPropertySource(properties = {
    "management.metrics.export.jmx.enabled=true",
    "management.metrics.export.prometheus.enabled=true",
    "management.metrics.export.prometheus.pushgateway.enabled=false",
})
public class CompositeMeterRegistryConfigurationTest {
    @Autowired
    private MeterRegistry registry;

    @Test
    public void compositeRegistryIsCreated() {
        assertThat(registry).isInstanceOf(CompositeMeterRegistry.class);

        assertThat(((CompositeMeterRegistry) registry).getRegistries())
            .hasAtLeastOneElementOfType(JmxMeterRegistry.class)
            .hasAtLeastOneElementOfType(PrometheusMeterRegistry.class);

        assertThat(registry.config().clock()).isNotNull();
    }

    @SpringBootApplication(scanBasePackages = "ignored")
    static class MetricsApp {
    }
}
