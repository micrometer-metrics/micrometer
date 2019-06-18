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
package io.micrometer.spring.autoconfigure.orm.jpa;

import io.micrometer.core.instrument.MeterRegistry;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.persistence.EntityManagerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HibernateMetricsAutoConfiguration}.
 *
 * @author Johnny Lim
 */
class HibernateMetricsAutoConfigurationTest {

    private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @AfterEach
    void cleanUp() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    void autoConfigureKicksIn() {
        registerAndRefresh(
            MeterRegistryConfiguration.class,
            EntityManagerFactoryConfiguration.class,
            HibernateMetricsAutoConfiguration.class);
        assertThat(context.getBean(HibernateMetricsAutoConfiguration.class)).isNotNull();
    }

    @Test
    void autoConfigureWhenMeterRegistryBeanIsNotPresentShouldBackOff() {
        registerAndRefresh(
            EntityManagerFactoryConfiguration.class,
            HibernateMetricsAutoConfiguration.class);
        assertThat(context.getBeansOfType(HibernateMetricsAutoConfiguration.class)).isEmpty();
    }

    @Test
    void autoConfigureWhenEntityManagerFactoryBeanIsNotPresentShouldBackOff() {
        registerAndRefresh(
            MeterRegistryConfiguration.class,
            HibernateMetricsAutoConfiguration.class);
        assertThat(context.getBeansOfType(HibernateMetricsAutoConfiguration.class)).isEmpty();
    }

    private void registerAndRefresh(Class<?>... configurationClasses) {
        this.context.register(configurationClasses);
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
    static class EntityManagerFactoryConfiguration {

        @Bean
        public EntityManagerFactory entityManagerFactory() {
            EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
            SessionFactory sessionFactory = mock(SessionFactory.class);
            when(sessionFactory.getStatistics()).thenReturn(mock(Statistics.class));
            when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
            return entityManagerFactory;
        }

    }

}
