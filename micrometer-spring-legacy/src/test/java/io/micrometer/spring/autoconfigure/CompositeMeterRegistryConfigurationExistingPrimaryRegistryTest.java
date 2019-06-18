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
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = CompositeMeterRegistryConfigurationExistingPrimaryRegistryTest.MetricsApp.class)
public class CompositeMeterRegistryConfigurationExistingPrimaryRegistryTest {
    
    @Autowired
    private MeterRegistry registry;

    @Test
    public void compositeNotCreatedWhenPrimaryRegistryExists() {
        assertThat(registry).isInstanceOf(SimpleMeterRegistry.class);
    }

    @SpringBootApplication(scanBasePackages = "ignored")
    static class MetricsApp {
        @Primary
        @Bean
        MeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        }
    }
}
