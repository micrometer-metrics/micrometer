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
package io.micrometer.spring.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = PropertiesMeterFilterIntegrationTest.MetricsApp.class)
@TestPropertySource(properties = {
    "management.metrics.enable[my.timer]=true", /* overriden by programmatic filter */
    "management.metrics.enable[my.counter]=false"
})
public class PropertiesMeterFilterIntegrationTest {

    @Autowired
    private MeterRegistry registry;

    @Test
    public void propertyBasedMeterFilters() {
        registry.counter("my.counter");
        assertThat(registry.find("my.counter").counter()).isNull();
    }

    @Test
    public void propertyBasedMeterFiltersCanTakeLowerPrecedenceThanProgrammaticallyBoundFilters() {
        registry.timer("my.timer");
        assertThat(registry.find("my.timer").meter()).isNull();
    }

    @SpringBootApplication(scanBasePackages = "ignore")
    static class MetricsApp {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        public MeterRegistryCustomizer meterFilter() {
            return r -> r.config().meterFilter(MeterFilter.deny(id -> id.getName().contains("my.timer")));
        }
    }
}
