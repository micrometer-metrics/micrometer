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
package io.micrometer.spring.autoconfigure.web.jetty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.embedded.AnnotationConfigEmbeddedWebApplicationContext;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.web.jetty.JettyServerThreadPoolMetricsBinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JettyMetricsAutoConfiguration}.
 *
 * @author Johnny Lim
 * @author Andy Wilkinson
 */
class JettyMetricsAutoConfigurationTest {

    private final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void autoConfiguresThreadPoolMetricsWithEmbeddedServletJetty() {
        AnnotationConfigEmbeddedWebApplicationContext context = new AnnotationConfigEmbeddedWebApplicationContext();
        context.register(MeterRegistryConfiguration.class, ServletWebServerConfiguration.class,
                JettyMetricsAutoConfiguration.class);
        context.refresh();

        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), null, context));
        assertThat(context.getBean(JettyServerThreadPoolMetricsBinder.class)).isNotNull();
        SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
        assertThat(registry.find("jetty.threads.config.min").meter()).isNotNull();

        context.close();
    }

    @Test
    public void allowsCustomJettyServerThreadPoolMetricsBinderToBeUsed() {
        registerAndRefresh(MeterRegistryConfiguration.class,
                CustomJettyServerThreadPoolMetricsBinder.class,
                JettyMetricsAutoConfiguration.class);
        assertThat(context.getBean(JettyServerThreadPoolMetricsBinder.class))
                .isEqualTo(context.getBean("customJettyServerThreadPoolMetricsBinder"));
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        context.register(configurationClasses);
        context.refresh();
    }

    @Configuration
    static class MeterRegistryConfiguration {

        @Bean
        public SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

    }

    @Configuration
    static class ServletWebServerConfiguration {

        @Bean
        public JettyEmbeddedServletContainerFactory jettyFactory() {
            return new JettyEmbeddedServletContainerFactory(0);
        }

    }

    @Configuration
    static class CustomJettyServerThreadPoolMetricsBinder {

        @Bean
        public JettyServerThreadPoolMetricsBinder customJettyServerThreadPoolMetricsBinder(
                MeterRegistry meterRegistry) {
            return new JettyServerThreadPoolMetricsBinder(meterRegistry);
        }

    }

}
