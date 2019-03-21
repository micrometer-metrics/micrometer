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
package io.micrometer.spring.autoconfigure.export.kairos;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.micrometer.core.instrument.Clock;
import io.micrometer.kairos.KairosConfig;
import io.micrometer.kairos.KairosMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link KairosMetricsExportAutoConfiguration}.
 *
 * @author Stephane Nicoll
 * @author Johnny Lim
 */
class KairosMetricsExportAutoConfigurationTest {

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
                .isThrownBy(() -> context.getBean(KairosMeterRegistry.class));
    }

    @Test
    void autoConfiguresItsConfigAndMeterRegistry() {
        registerAndRefresh(BaseConfiguration.class);
        assertThat(context.getBean(KairosMeterRegistry.class)).isNotNull();
        assertThat(context.getBean(KairosConfig.class)).isNotNull();
    }

    @Test
    void autoConfigurationCanBeDisabled() {
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.export.kairos.enabled=false");
        registerAndRefresh(BaseConfiguration.class);
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                .isThrownBy(() -> context.getBean(KairosMeterRegistry.class));
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                .isThrownBy(() -> context.getBean(KairosConfig.class));
    }

    @Test
    void allowsCustomConfigToBeUsed() {
        registerAndRefresh(CustomConfigConfiguration.class);
        assertThat(context.getBean(KairosMeterRegistry.class)).isNotNull();
        assertThat(context.getBean(KairosConfig.class)).isEqualTo(context.getBean("customConfig"));
    }

    @Test
    void allowsCustomRegistryToBeUsed() {
        registerAndRefresh(CustomRegistryConfiguration.class);
        assertThat(context.getBean(KairosMeterRegistry.class)).isEqualTo(context.getBean("customRegistry"));
        assertThat(context.getBean(KairosConfig.class)).isNotNull();
    }

    @Test
    void stopsMeterRegistryWhenContextIsClosed() {
        registerAndRefresh(BaseConfiguration.class);
        KairosMeterRegistry registry = context.getBean(KairosMeterRegistry.class);
        assertThat(registry.isClosed()).isFalse();
        context.close();
        assertThat(registry.isClosed()).isTrue();
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        if (configurationClasses.length > 0) {
            context.register(configurationClasses);
        }
        context.register(KairosMetricsExportAutoConfiguration.class);
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
        public KairosConfig customConfig() {
            return (key) -> null;
        }

    }

    @Configuration
    @Import(BaseConfiguration.class)
    static class CustomRegistryConfiguration {

        @Bean
        public KairosMeterRegistry customRegistry(KairosConfig config, Clock clock) {
            return new KairosMeterRegistry(config, clock);
        }

    }

}
