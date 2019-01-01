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
package io.micrometer.spring.autoconfigure.export.appoptics;

import java.util.Map;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import io.micrometer.appoptics.AppOpticsConfig;
import io.micrometer.appoptics.AppOpticsMeterRegistry;
import io.micrometer.core.instrument.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link AppOpticsMetricsExportAutoConfiguration}.
 *
 * @author Stephane Nicoll
 * @author Johnny Lim
 */
class AppOpticsMetricsExportAutoConfigurationTest {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void backsOffWithoutAClock() {
        registerAndRefresh();
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                .isThrownBy(() -> context.getBean(AppOpticsMeterRegistry.class));
    }

    @Test
    void autoConfiguresItsConfigAndMeterRegistry() {
        registerAndRefresh(BaseConfiguration.class);
        assertThat(context.getBean(AppOpticsMeterRegistry.class)).isNotNull();
        assertThat(context.getBean(AppOpticsConfig.class)).isNotNull();
    }

    @Test
    void autoConfigurationCanBeDisabled() {
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.export.appoptics.enabled=false");
        registerAndRefresh(BaseConfiguration.class);
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                .isThrownBy(() -> context.getBean(AppOpticsMeterRegistry.class));
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                .isThrownBy(() -> context.getBean(AppOpticsConfig.class));
    }

    @Test
    void allowsCustomConfigToBeUsed() {
        registerAndRefresh(CustomConfigConfiguration.class);
        assertThat(context.getBean(AppOpticsMeterRegistry.class)).isNotNull();
        assertThat(context.getBean(AppOpticsConfig.class)).isEqualTo(context.getBean("customConfig"));
    }

    @Test
    void allowsCustomRegistryToBeUsed() {
        registerAndRefresh(CustomRegistryConfiguration.class);
        assertThat(context.getBean(AppOpticsMeterRegistry.class)).isEqualTo(context.getBean("customRegistry"));
        assertThat(context.getBean(AppOpticsConfig.class)).isNotNull();
    }

    @Test
    void stopsMeterRegistryWhenContextIsClosed() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        registerAndRefresh(context, BaseConfiguration.class);
        AppOpticsMeterRegistry registry = spyOnDisposableBean(AppOpticsMeterRegistry.class, context);
        context.close();
        verify(registry).stop();
    }

    private void registerAndRefresh(AnnotationConfigApplicationContext context,
            Class<?>... configurationClasses) {
        if (configurationClasses.length > 0) {
            context.register(configurationClasses);
        }
        context.register(AppOpticsMetricsExportAutoConfiguration.class);
        context.refresh();
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        registerAndRefresh(context, configurationClasses);
    }

    @SuppressWarnings("unchecked")
    private <T> T spyOnDisposableBean(Class<T> type, AnnotationConfigApplicationContext context) {
        String[] names = context.getBeanNamesForType(type);
        assertThat(names).hasSize(1);
        String registryBeanName = names[0];
        Map<String, Object> disposableBeans = (Map<String, Object>) ReflectionTestUtils
                .getField(context.getAutowireCapableBeanFactory(), "disposableBeans");
        Object registryAdapter = disposableBeans.get(registryBeanName);
        T registry = (T) spy(ReflectionTestUtils.getField(registryAdapter, "bean"));
        ReflectionTestUtils.setField(registryAdapter, "bean", registry);
        return registry;
    }

    @Configuration
    static class BaseConfiguration {

        @Bean
        public Clock clock() {
            return Clock.SYSTEM;
        }

    }

    @Configuration
    @Import(BaseConfiguration.class)
    static class CustomConfigConfiguration {

        @Bean
        public AppOpticsConfig customConfig() {
            return (key) -> null;
        }

    }

    @Configuration
    @Import(BaseConfiguration.class)
    static class CustomRegistryConfiguration {

        @Bean
        public AppOpticsMeterRegistry customRegistry(AppOpticsConfig config,
                Clock clock) {
            return new AppOpticsMeterRegistry(config, clock);
        }

    }

}
