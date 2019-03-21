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
package io.micrometer.spring.autoconfigure.jdbc;

import javax.sql.DataSource;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link DataSourcePoolMetricsAutoConfiguration}.
 *
 * @author Johnny Lim
 */
class DataSourcePoolMetricsAutoConfigurationTest {

    private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @AfterEach
    void cleanUp() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    void backsOffWhenMeterRegistryIsMissing() {
        registerAndRefresh(DataSourceConfiguration.class,
                DataSourcePoolMetricsAutoConfiguration.class);

        assertThatThrownBy(() -> this.context.getBean(DataSourcePoolMetricsAutoConfiguration.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void backsOffWhenDataSourceIsMissing() {
        registerAndRefresh(MeterRegistryConfiguration.class,
                DataSourcePoolMetricsAutoConfiguration.class);

        assertThatThrownBy(() -> this.context.getBean(DataSourcePoolMetricsAutoConfiguration.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void autoConfigurationKicksIn() {
        registerAndRefresh(DataSourceConfiguration.class, MeterRegistryConfiguration.class,
                DataSourcePoolMetricsAutoConfiguration.class);

        assertThat(this.context.getBean(DataSourcePoolMetricsAutoConfiguration.class)).isNotNull();
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        this.context.register(configurationClasses);
        this.context.refresh();
    }

    @Configuration
    static class DataSourceConfiguration {

        @Bean
        public DataSource dataSource() {
            return mock(DataSource.class);
        }

    }

    @Configuration
    static class MeterRegistryConfiguration {

        @Bean
        public MeterRegistry meterRegistry() {
            return mock(MeterRegistry.class);
        }

    }

}
