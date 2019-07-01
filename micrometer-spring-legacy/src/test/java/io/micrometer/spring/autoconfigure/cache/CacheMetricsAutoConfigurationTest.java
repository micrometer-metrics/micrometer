/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.spring.autoconfigure.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.cache.CacheMetricsRegistrar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link CacheMetricsAutoConfiguration}.
 *
 * @author Johnny Lim
 */
class CacheMetricsAutoConfigurationTest {

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
                CacheManagerConfiguration.class,
                MeterRegistryConfiguration.class,
                CacheMetricsAutoConfiguration.class);
        assertThat(context.getBean(CacheMetricsRegistrar.class)).isNotNull();
    }

    @Test
    void backsOffWhenMeterRegistryIsMissing() {
        registerAndRefresh(
                CacheManagerConfiguration.class,
                CacheMetricsAutoConfiguration.class);
        assertThat(context.getBeansOfType(CacheMetricsRegistrar.class)).isEmpty();
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        context.register(configurationClasses);
        context.refresh();
    }

    @Configuration
    static class CacheManagerConfiguration {

        @Bean
        public CacheManager cacheManager() {
            return mock(CacheManager.class);
        }

    }

    @Configuration
    static class MeterRegistryConfiguration {

        @Bean
        public SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

    }

}
