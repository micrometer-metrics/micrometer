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
package io.micrometer.spring.autoconfigure.export.appoptics;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.micrometer.appoptics.AppOpticsConfig;
import io.micrometer.appoptics.AppOpticsMeterRegistry;
import io.micrometer.core.instrument.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.export.appoptics.api-token=fakeToken");
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
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.export.appoptics.api-token=fakeToken");
        registerAndRefresh(CustomRegistryConfiguration.class);
        assertThat(context.getBean(AppOpticsMeterRegistry.class)).isEqualTo(context.getBean("customRegistry"));
        assertThat(context.getBean(AppOpticsConfig.class)).isNotNull();
    }

    @Test
    public void stopsMeterRegistryWhenContextIsClosed() {
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.export.appoptics.api-token=fakeToken");
        registerAndRefresh(BaseConfiguration.class);
        AppOpticsMeterRegistry registry = context.getBean(AppOpticsMeterRegistry.class);
        assertThat(registry.isClosed()).isFalse();
        context.close();
        assertThat(registry.isClosed()).isTrue();
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        if (configurationClasses.length > 0) {
            context.register(configurationClasses);
        }
        context.register(AppOpticsMetricsExportAutoConfiguration.class);
        context.refresh();
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
            return new AppOpticsConfig() {
                @Override
                public String get(String key) {
                    return null;
                }

                @Override
                public String apiToken() {
                    return "fake";
                }
            };
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
