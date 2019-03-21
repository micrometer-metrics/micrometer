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
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SystemMetricsAutoConfiguration}.
 *
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Johnny Lim
 */
public class SystemMetricsAutoConfigurationTest {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void autoConfiguresUptimeMetrics() {
        registerAndRefresh();
        assertThat(context.getBean(UptimeMetrics.class)).isNotNull();
    }

    @Test
    @Deprecated
    public void allowsUptimeMetricsToBeDisabled() {
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.binders.uptime.enabled=false");
        registerAndRefresh();
        assertThat(context.getBeansOfType(UptimeMetrics.class)).isEmpty();
    }

    @Test
    public void allowsCustomUptimeMetricsToBeUsed() {
        registerAndRefresh(CustomUptimeMetricsConfiguration.class);
        assertThat(context.getBean(UptimeMetrics.class)).isEqualTo(context.getBean("customUptimeMetrics"));
    }

    @Test
    public void autoConfiguresProcessorMetrics() {
        registerAndRefresh();
        assertThat(context.getBean(ProcessorMetrics.class)).isNotNull();
    }

    @Test
    @Deprecated
    public void allowsProcessorMetricsToBeDisabled() {
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.binders.processor.enabled=false");
        registerAndRefresh();
        assertThat(context.getBeansOfType(ProcessorMetrics.class)).isEmpty();
    }

    @Test
    public void allowsCustomProcessorMetricsToBeUsed() {
        registerAndRefresh(CustomProcessorMetricsConfiguration.class);
        assertThat(context.getBean(ProcessorMetrics.class)).isEqualTo(context.getBean("customProcessorMetrics"));
    }

    @Test
    public void autoConfiguresFileDescriptorMetrics() {
        registerAndRefresh();
        assertThat(context.getBean(FileDescriptorMetrics.class)).isNotNull();
    }

    @Test
    @Deprecated
    public void allowsFileDescriptorMetricsToBeDisabled() {
        EnvironmentTestUtils.addEnvironment(context, "management.metrics.binders.files.enabled=false");
        registerAndRefresh();
        assertThat(context.getBeansOfType(FileDescriptorMetrics.class)).isEmpty();
    }

    @Test
    public void allowsCustomFileDescriptorMetricsToBeUsed() {
        registerAndRefresh(CustomFileDescriptorMetricsConfiguration.class);
        assertThat(context.getBean(FileDescriptorMetrics.class)).isEqualTo(context.getBean("customFileDescriptorMetrics"));
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        if (configurationClasses.length != 0) {
            this.context.register(configurationClasses);
        }
        this.context.register(MeterRegistryConfiguration.class, SystemMetricsAutoConfiguration.class);
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
    static class CustomUptimeMetricsConfiguration {

        @Bean
        public UptimeMetrics customUptimeMetrics() {
            return new UptimeMetrics();
        }

    }

    @Configuration
    static class CustomProcessorMetricsConfiguration {

        @Bean
        public ProcessorMetrics customProcessorMetrics() {
            return new ProcessorMetrics();
        }

    }

    @Configuration
    static class CustomFileDescriptorMetricsConfiguration {

        @Bean
        public FileDescriptorMetrics customFileDescriptorMetrics() {
            return new FileDescriptorMetrics();
        }

    }

}
