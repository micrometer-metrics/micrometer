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
package io.micrometer.spring.autoconfigure.web.client;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import io.micrometer.spring.web.client.DefaultRestTemplateExchangeTagsProvider;
import io.micrometer.spring.web.client.MetricsRestTemplateCustomizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RestTemplateMetricsAutoConfiguration}.
 *
 * @author Raheela Aslam
 * @author Johnny Lim
 */
class RestTemplateMetricsAutoConfigurationTest {

    private final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void autoConfigureWorks() {
        registerAndRefresh(
            MetricsPropertiesConfiguration.class,
            MeterRegistryConfiguration.class,
            RestTemplateBuilderConfiguration.class,
            RestTemplateMetricsAutoConfiguration.class);
        assertThat(context.getBean(DefaultRestTemplateExchangeTagsProvider.class)).isNotNull();
        assertThat(context.getBean(MetricsRestTemplateCustomizer.class)).isNotNull();
    }

    @Test
    void backsOffWhenRestTemplateBuilderIsMissing() {
        registerAndRefresh(
            MetricsPropertiesConfiguration.class,
            MeterRegistryConfiguration.class,
            RestTemplateMetricsAutoConfiguration.class);
        assertThat(context.getBeansOfType(DefaultRestTemplateExchangeTagsProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(MetricsRestTemplateCustomizer.class)).isEmpty();
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
    @EnableConfigurationProperties(MetricsProperties.class)
    static class MetricsPropertiesConfiguration {
    }

    @Configuration
    static class RestTemplateBuilderConfiguration {

        @Bean
        public RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder();
        }

    }

}
