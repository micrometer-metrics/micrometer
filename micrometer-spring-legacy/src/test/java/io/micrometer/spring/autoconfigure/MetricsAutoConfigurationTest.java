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
package io.micrometer.spring.autoconfigure;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.logging.Log4j2Metrics;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.PropertiesMeterFilter;
import io.micrometer.spring.scheduling.ScheduledMethodMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MetricsAutoConfiguration}.
 *
 * @author Andy Wilkinson
 * @author Johnny Lim
 */
class MetricsAutoConfigurationTest {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void autoConfiguresAClock() {
        registerAndRefresh(BaseMeterRegistryConfiguration.class);
        assertThat(context.getBean(Clock.class)).isNotNull();
    }

    @Test
    void allowsACustomClockToBeUsed() {
        registerAndRefresh(BaseMeterRegistryConfiguration.class, CustomClockConfiguration.class);
        assertThat(context.getBean(Clock.class)).isEqualTo(context.getBean("customClock"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void configuresMeterRegistries() {
        registerAndRefresh(CustomMeterRegistryConfiguration.class);
        MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
        List<MeterFilter> filters = (List<MeterFilter>) ReflectionTestUtils.getField(meterRegistry, "filters");
        assertThat(filters).hasSize(3);
        assertThat(filters.get(0).accept((Meter.Id) null)).isEqualTo(MeterFilterReply.DENY);
        assertThat(filters.get(1)).isInstanceOf(PropertiesMeterFilter.class);
        assertThat(filters.get(2).accept((Meter.Id) null)).isEqualTo(MeterFilterReply.ACCEPT);
        verify((MeterBinder) context.getBean("meterBinder")).bindTo(meterRegistry);
        verify(context.getBean(MeterRegistryCustomizer.class)).customize(meterRegistry);
    }

    @Test
    void backsOffWhenSpringAopEnabledIsFalse() {
        EnvironmentTestUtils.addEnvironment(context, "spring.aop.auto=false");

        registerAndRefresh(BaseMeterRegistryConfiguration.class);

        assertThatThrownBy(() -> context.getBean(ScheduledMethodMetrics.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void backsOffWhenScheduledEnabledIsFalse() {
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.binders.scheduled.enabled=false");

        registerAndRefresh(BaseMeterRegistryConfiguration.class);

        assertThatThrownBy(() -> context.getBean(ScheduledMethodMetrics.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    public void automaticallyRegisteredBinders() {
        assertThat(context.getBeansOfType(MeterBinder.class).values())
                .hasAtLeastOneElementOfType(Log4j2Metrics.class)
                .hasAtLeastOneElementOfType(LogbackMetrics.class)
                .hasAtLeastOneElementOfType(JvmGcMetrics.class)
                .hasAtLeastOneElementOfType(JvmThreadMetrics.class)
                .hasAtLeastOneElementOfType(ClassLoaderMetrics.class)
                .hasAtLeastOneElementOfType(UptimeMetrics.class)
                .hasAtLeastOneElementOfType(ProcessorMetrics.class)
                .hasAtLeastOneElementOfType(FileDescriptorMetrics.class);
    }

    @Test
    void backsOffWhenCustomScheduledMethodMetricsIsProvided() {
        registerAndRefresh(BaseMeterRegistryConfiguration.class, ScheduledMethodMetricsConfiguration.class);

        assertThat(context.getBean(ScheduledMethodMetrics.class)).isInstanceOf(MyScheduledMethodMetrics.class);
    }

    @Test
    void scheduledMethodMetricsIsAvailableByDefault() {
        registerAndRefresh(BaseMeterRegistryConfiguration.class);

        assertThat(context.getBean(ScheduledMethodMetrics.class)).isInstanceOf(ScheduledMethodMetrics.class);
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        if (configurationClasses.length > 0) {
            this.context.register(configurationClasses);
        }
        this.context.register(MetricsAutoConfiguration.class);
        this.context.refresh();
    }

    @Configuration
    static class BaseMeterRegistryConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
            return spy(meterRegistry);
        }

    }

    @Configuration
    static class CustomClockConfiguration {

        @Bean
        Clock customClock() {
            return Clock.SYSTEM;
        }

    }

    @Configuration
    static class CustomMeterRegistryConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
            return spy(meterRegistry);
        }

        @Bean
        @SuppressWarnings("rawtypes")
        MeterRegistryCustomizer meterRegistryCustomizer() {
            return mock(MeterRegistryCustomizer.class);
        }

        @Bean
        MeterBinder meterBinder() {
            return mock(MeterBinder.class);
        }

        @Bean
        @Order(1)
        MeterFilter acceptMeterFilter() {
            return MeterFilter.accept();
        }

        @Bean
        @Order(-1)
        MeterFilter denyMeterFilter() {
            return MeterFilter.deny();
        }

    }

    @Configuration
    static class ScheduledMethodMetricsConfiguration {

        @Bean
        public ScheduledMethodMetrics scheduledMethodMetrics(MeterRegistry registry) {
            return new MyScheduledMethodMetrics(registry);
        }

    }

    static class MyScheduledMethodMetrics extends ScheduledMethodMetrics {

        public MyScheduledMethodMetrics(MeterRegistry registry) {
            super(registry);
        }

    }

}
