/**
 * Copyright 2019 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = SingleRegistryDoubleCountingTest.MetricsApp.class)
@TestPropertySource(properties = {
        "management.metrics.export.prometheus.enabled=true",
        "management.metrics.export.prometheus.pushgateway.enabled=false",
})
public class SingleRegistryDoubleCountingTest {
    @Autowired
    private MeterRegistry registry;

    @Autowired
    private ApplicationContext context;

    @Test
    public void singleRegistryIsCreated() {
        assertThat(registry).isNotInstanceOf(CompositeMeterRegistry.class);

        assertThat(registry.config().clock()).isNotNull();
    }

    @Test
    public void metricsAreNotCountedTwice() {
        Logger logger = LoggerFactory.getLogger("test-logger");
        logger.error("Error.");

        Map<String, MeterRegistry> registriesByName = context
                .getBeansOfType(MeterRegistry.class);
        assertThat(registriesByName).hasSize(1);
        registriesByName.forEach((name, registry) ->
                assertThat(registry
                        .get("logback.events")
                        .tag("level", "error")
                        .counter()
                        .count())
                        .isEqualTo(1));
    }

    @SpringBootApplication(scanBasePackages = "ignored")
    static class MetricsApp {
    }
}
