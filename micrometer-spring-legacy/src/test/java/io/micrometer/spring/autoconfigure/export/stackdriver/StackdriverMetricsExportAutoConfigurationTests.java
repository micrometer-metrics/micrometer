/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure.export.stackdriver;

import io.micrometer.core.instrument.Clock;
import io.micrometer.stackdriver.StackdriverMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link StackdriverMetricsExportAutoConfiguration}.
 *
 * @author Johnny Lim
 */
public class StackdriverMetricsExportAutoConfigurationTests {

    private final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

    @AfterEach
    void cleanUp() {
        context.close();
    }

    @Test
    void autoConfigureByDefault() {
        EnvironmentTestUtils.addEnvironment(context,
                "management.metrics.export.stackdriver.project-id=my-project-id");

        registerAndRefresh();

        assertThat(context.getBean(StackdriverMeterRegistry.class)).isNotNull();
    }

    @Test
    void autoConfigureDisabledByProperty() {
        EnvironmentTestUtils.addEnvironment(context,
                "management.metrics.export.stackdriver.project-id=my-project-id",
                "management.metrics.export.stackdriver.enabled=false");

        registerAndRefresh();

        assertThatThrownBy(() -> context.getBean(StackdriverMeterRegistry.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void injectedClockShouldBeUsed() {
        EnvironmentTestUtils.addEnvironment(context,
                "management.metrics.export.stackdriver.project-id=my-project-id");

        registerAndRefresh();

        StackdriverMeterRegistry meterRegistry = context.getBean(StackdriverMeterRegistry.class);
        assertThat(meterRegistry).hasFieldOrPropertyWithValue("clock", context.getBean(Clock.class));
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        if (configurationClasses.length > 0) {
            context.register(configurationClasses);
        }
        context.register(ClockConfiguration.class, StackdriverMetricsExportAutoConfiguration.class);
        context.refresh();
    }

    @Configuration
    static class ClockConfiguration {

        @Bean
        public Clock clock() {
            return mock(Clock.class);
        }

    }

}
