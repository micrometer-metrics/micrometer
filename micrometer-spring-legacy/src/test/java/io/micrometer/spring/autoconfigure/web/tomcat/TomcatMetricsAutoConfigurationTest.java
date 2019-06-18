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
package io.micrometer.spring.autoconfigure.web.tomcat;

import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.embedded.AnnotationConfigEmbeddedWebApplicationContext;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.tomcat.TomcatMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.web.tomcat.TomcatMetricsBinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TomcatMetricsAutoConfiguration}.
 *
 * @author Andy Wilkinson
 * @author Johnny Lim
 */
class TomcatMetricsAutoConfigurationTest {

    private final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void autoConfiguresTomcatMetricsWithEmbeddedServletTomcat() {
        AnnotationConfigEmbeddedWebApplicationContext context = new AnnotationConfigEmbeddedWebApplicationContext();
        context.register(MeterRegistryConfiguration.class, ServletWebServerConfiguration.class,
                TomcatMetricsAutoConfiguration.class);
        context.refresh();

        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), null, context));
        assertThat(context.getBean(TomcatMetricsBinder.class)).isNotNull();
        SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
        assertThat(registry.find("tomcat.sessions.active.max").meter()).isNotNull();
        assertThat(registry.find("tomcat.threads.current").meter()).isNotNull();

        context.close();
    }

    @Test
    void autoConfiguresTomcatMetricsWithStandaloneTomcat() {
        registerAndRefresh(MeterRegistryConfiguration.class, TomcatMetricsAutoConfiguration.class);
        assertThat(context.getBean(TomcatMetricsBinder.class)).isNotNull();
    }

    @Test
    void allowsCustomTomcatMetricsBinderToBeUsed() {
        registerAndRefresh(MeterRegistryConfiguration.class, CustomTomcatMetricsBinder.class,
                TomcatMetricsAutoConfiguration.class);
        assertThat(context.getBean(TomcatMetricsBinder.class))
                .isEqualTo(context.getBean("customTomcatMetricsBinder"));
    }

    @Test
    void allowsCustomTomcatMetricsToBeUsed() {
        registerAndRefresh(MeterRegistryConfiguration.class, CustomTomcatMetrics.class,
                TomcatMetricsAutoConfiguration.class);
        assertThat(context.getBean(TomcatMetrics.class))
                .isEqualTo(context.getBean("customTomcatMetrics"));
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
        public TomcatEmbeddedServletContainerFactory tomcatFactory() {
            return new TomcatEmbeddedServletContainerFactory(0);
        }

    }

    @Configuration
    static class CustomTomcatMetrics {

        @Bean
        public TomcatMetrics customTomcatMetrics() {
            return new TomcatMetrics(null, Collections.emptyList());
        }

    }

    @Configuration
    static class CustomTomcatMetricsBinder {

        @Bean
        public TomcatMetricsBinder customTomcatMetricsBinder(MeterRegistry meterRegistry) {
            return new TomcatMetricsBinder(meterRegistry);
        }

    }

}
