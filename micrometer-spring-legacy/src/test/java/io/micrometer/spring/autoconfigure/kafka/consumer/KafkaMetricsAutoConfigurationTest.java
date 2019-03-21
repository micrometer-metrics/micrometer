/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure.kafka.consumer;

import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaConsumerMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link KafkaMetricsAutoConfiguration}.
 */
public class KafkaMetricsAutoConfigurationTest {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @AfterEach
    void cleanUp() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void whenThereIsNoMBeanServerAutoConfigurationBacksOff() {
        registerAndRefresh();
        assertThat(context.getBeansOfType(KafkaConsumerMetrics.class)).isEmpty();
    }

    @Test
    public void whenThereIsAnMBeanServerKafkaConsumerMetricsIsConfigured() {
        registerAndRefresh(JmxAutoConfiguration.class);
        assertThat(context.getBean(KafkaConsumerMetrics.class)).isNotNull();
    }

    @Test
    public void allowsCustomKafkaConsumerMetricsToBeUsed() {
        registerAndRefresh(JmxAutoConfiguration.class, CustomKafkaConsumerMetricsConfiguration.class);
        assertThat(context.getBean(KafkaConsumerMetrics.class)).isEqualTo(context.getBean("customKafkaConsumerMetrics"));
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        if (configurationClasses.length != 0) {
            this.context.register(configurationClasses);
        }
        this.context.register(MeterRegistryConfiguration.class, KafkaMetricsAutoConfiguration.class);
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
    static class CustomKafkaConsumerMetricsConfiguration {

        @Bean
        public KafkaConsumerMetrics customKafkaConsumerMetrics() {
            return new KafkaConsumerMetrics();
        }

    }

}
