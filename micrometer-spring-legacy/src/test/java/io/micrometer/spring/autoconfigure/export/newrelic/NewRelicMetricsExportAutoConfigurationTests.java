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

package io.micrometer.spring.autoconfigure.export.newrelic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.newrelic.NewRelicConfig;
import io.micrometer.newrelic.NewRelicMeterRegistry;

/**
 *
 * Tests for {@link NewRelicMetricsExportAutoConfiguration}.
 *
 * @author Neil Powell
 */
class NewRelicMetricsExportAutoConfigurationTests {

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
                .isThrownBy(() -> context.getBean(NewRelicMeterRegistry.class));
    }
    
    @Test
    void failsWithoutAnApiKey() {
        EnvironmentTestUtils.addEnvironment(context, 
                "management.metrics.export.newrelic.account-id=12345");
        Exception exception = assertThrows(BeanCreationException.class, () -> {
            registerAndRefresh(BaseConfiguration.class);
        });
        assertThat(exception.getCause().getCause()).isInstanceOf(MissingRequiredConfigurationException.class);
        assertThat(exception.getMessage()).contains("apiKey");
    }

    @Test
    void failsWithoutAnAccountId() {
        EnvironmentTestUtils.addEnvironment(context, 
                "management.metrics.export.newrelic.api-key=abcde");
        Exception exception = assertThrows(BeanCreationException.class, () -> {
            registerAndRefresh(BaseConfiguration.class);
        });
        assertThat(exception.getCause().getCause()).isInstanceOf(MissingRequiredConfigurationException.class);
        assertThat(exception.getMessage()).contains("accountId");
    }

    @Test
    void failsToAutoConfigureWithoutEventType() {
        EnvironmentTestUtils.addEnvironment(context, 
                "management.metrics.export.newrelic.api-key=abcde",
                "management.metrics.export.newrelic.account-id=12345",
                "management.metrics.export.newrelic.event-type=");
        Exception exception = assertThrows(BeanCreationException.class, () -> {
            registerAndRefresh(BaseConfiguration.class);
        });
        assertThat(exception.getCause().getCause()).isInstanceOf(MissingRequiredConfigurationException.class);
        assertThat(exception.getMessage()).contains("eventType");
    }

    @Test
    void autoConfiguresWithEventTypeOverriden() {
        EnvironmentTestUtils.addEnvironment(context, 
                "management.metrics.export.newrelic.api-key=abcde",
                "management.metrics.export.newrelic.account-id=12345",
                "management.metrics.export.newrelic.event-type=wxyz");
        registerAndRefresh(BaseConfiguration.class);
        assertThat(context.getBean(NewRelicMeterRegistry.class)).isNotNull();
        assertThat(context.getBean(Clock.class)).isNotNull();
        assertThat(context.getBean(NewRelicConfig.class)).isNotNull();
    }

    @Test
    void autoConfiguresWithMeterNameEventTypeEnabledAndWithoutEventType() {
        EnvironmentTestUtils.addEnvironment(context, 
                "management.metrics.export.newrelic.api-key=abcde",
                "management.metrics.export.newrelic.account-id=12345",
                "management.metrics.export.newrelic.event-type=",
                "management.metrics.export.newrelic.meter-name-event-type-enabled=true");
        registerAndRefresh(BaseConfiguration.class);
        assertThat(context.getBean(NewRelicMeterRegistry.class)).isNotNull();
        assertThat(context.getBean(Clock.class)).isNotNull();
        assertThat(context.getBean(NewRelicConfig.class)).isNotNull();
    }

    @Test
    void autoConfiguresWithAccountIdAndApiKey() {
        EnvironmentTestUtils.addEnvironment(context, 
                "management.metrics.export.newrelic.api-key=abcde",
                "management.metrics.export.newrelic.account-id=12345");
        registerAndRefresh(BaseConfiguration.class);
        assertThat(context.getBean(NewRelicMeterRegistry.class)).isNotNull();
        assertThat(context.getBean(Clock.class)).isNotNull();
        assertThat(context.getBean(NewRelicConfig.class)).isNotNull();
    }

    @Test
    void autoConfigurationCanBeDisabled() {
        EnvironmentTestUtils.addEnvironment(context, 
                "management.metrics.export.newrelic.enabled=false");
        registerAndRefresh(BaseConfiguration.class);
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
            .isThrownBy(() -> context.getBean(NewRelicMeterRegistry.class));
    }

    @Test
    void allowsConfigToBeCustomized() {
        EnvironmentTestUtils.addEnvironment(context, 
                "management.metrics.export.newrelic.api-key=abcde",
                "management.metrics.export.newrelic.account-id=12345");
        registerAndRefresh(CustomConfigConfiguration.class);
        assertThat(context.getBean(NewRelicMeterRegistry.class)).isNotNull();
        assertThat(context.getBean(NewRelicConfig.class)).isEqualTo(context.getBean("customConfig"));
    }

    @Test
    void allowsRegistryToBeCustomized() {
        EnvironmentTestUtils.addEnvironment(context, 
                "management.metrics.export.newrelic.api-key=abcde",
                "management.metrics.export.newrelic.account-id=12345");
        registerAndRefresh(CustomRegistryConfiguration.class);
        assertThat(context.getBean(NewRelicMeterRegistry.class)).isEqualTo(context.getBean("customRegistry"));
        assertThat(context.getBean(NewRelicConfig.class)).isNotNull();
    }

    @Test
    void stopsMeterRegistryWhenContextIsClosed() {
        EnvironmentTestUtils.addEnvironment(context, 
                "management.metrics.export.newrelic.api-key=abcde",
                "management.metrics.export.newrelic.account-id=abcde");
        registerAndRefresh(BaseConfiguration.class);
        NewRelicMeterRegistry registry = context.getBean(NewRelicMeterRegistry.class);
        assertThat(registry.isClosed()).isFalse();
        context.close();
        assertThat(registry.isClosed()).isTrue();
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        if (configurationClasses.length > 0) {
            context.register(configurationClasses);
        }
        context.register(NewRelicMetricsExportAutoConfiguration.class);
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
        NewRelicConfig customConfig() {
            return (key) -> {
                if ("newrelic.accountId".equals(key)) {
                    return "abcde";
                }
                if ("newrelic.apiKey".equals(key)) {
                    return "12345";
                }
                return null;
            };
        }
    }

    @Configuration
    @Import(BaseConfiguration.class)
    static class CustomRegistryConfiguration {
        @Bean
        NewRelicMeterRegistry customRegistry(NewRelicConfig config, Clock clock) {
            return new NewRelicMeterRegistry(config, clock);
        }
    }
}
