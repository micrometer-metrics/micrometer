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
package io.micrometer.spring.autoconfigure;

import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link LogbackMetricsAutoConfiguration}.
 *
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Johnny Lim
 */
public class LogbackMetricsAutoConfigurationTest {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void autoConfiguresLogbackMetrics() {
        registerAndRefresh();
        assertThat(context.getBean(LogbackMetrics.class)).isNotNull();
    }

    @Test
    @Deprecated
    public void allowsLogbackMetricsToBeDisabled() {
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.binders.logback.enabled=false");
        registerAndRefresh();
        assertThat(context.getBeansOfType(LogbackMetrics.class)).isEmpty();
    }

    @Test
    public void allowsCustomLogbackMetricsToBeUsed() {
        registerAndRefresh(CustomLogbackMetricsConfiguration.class);
        assertThat(context.getBean(LogbackMetrics.class)).isEqualTo(context.getBean("customLogbackMetrics"));
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        if (configurationClasses.length != 0) {
            this.context.register(configurationClasses);
        }
        this.context.register(MeterRegistryConfiguration.class, LogbackMetricsAutoConfiguration.class);
        this.context.refresh();
    }

    @Configuration
    static class MeterRegistryConfiguration {

        @Bean
        public MeterRegistry meterRegistry() {
            return mock(MeterRegistry.class);
        }

    }

    @Configuration
    static class CustomLogbackMetricsConfiguration {

        @Bean
        public LogbackMetrics customLogbackMetrics() {
            return new LogbackMetrics();
        }

    }

}
