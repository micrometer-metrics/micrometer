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
package io.micrometer.spring.autoconfigure.orm.jpa;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jpa.HibernateMetrics;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.hibernate.SessionFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;

import java.util.Collections;
import java.util.Map;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for metrics on all available
 * Hibernate {@link EntityManagerFactory} instances that have statistics enabled.
 *
 * @author Rui Figueira
 * @author Stephane Nicoll
 * @author Johnny Lim
 * @since 1.1.0
 */
@Configuration
@AutoConfigureAfter({MetricsAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        SimpleMetricsExportAutoConfiguration.class})
@ConditionalOnClass({EntityManagerFactory.class, SessionFactory.class, MeterRegistry.class})
@Conditional(HibernateMetricsAutoConfiguration.HibernateMetricsConditionalOnBeans.class)
public class HibernateMetricsAutoConfiguration {
    private static final String ENTITY_MANAGER_FACTORY_SUFFIX = "entityManagerFactory";

    private final MeterRegistry registry;

    public HibernateMetricsAutoConfiguration(MeterRegistry registry) {
        this.registry = registry;
    }

    @Autowired
    public void bindEntityManagerFactoriesToRegistry(Map<String, EntityManagerFactory> entityManagerFactories) {
        entityManagerFactories.forEach(this::bindEntityManagerFactoryToRegistry);
    }

    private void bindEntityManagerFactoryToRegistry(String beanName, EntityManagerFactory entityManagerFactory) {
        String entityManagerFactoryName = getEntityManagerFactoryName(beanName);
        try {
            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            new HibernateMetrics(sessionFactory, entityManagerFactoryName,
                Collections.emptyList()).bindTo(this.registry);
        }
        catch (PersistenceException ex) {
        }
    }

    /**
     * Get the name of an {@link EntityManagerFactory} based on its {@code beanName}.
     *
     * @param beanName the name of the {@link EntityManagerFactory} bean
     * @return a name for the given entity manager factory
     */
    private String getEntityManagerFactoryName(String beanName) {
        if (beanName.length() > ENTITY_MANAGER_FACTORY_SUFFIX.length() &&
                StringUtils.endsWithIgnoreCase(beanName, ENTITY_MANAGER_FACTORY_SUFFIX)) {
            return beanName.substring(0, beanName.length() - ENTITY_MANAGER_FACTORY_SUFFIX.length());
        }
        return beanName;
    }

    static class HibernateMetricsConditionalOnBeans extends AllNestedConditions {

        HibernateMetricsConditionalOnBeans() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnBean(MeterRegistry.class)
        static class ConditionalOnMeterRegistryBean {
        }

        @ConditionalOnBean(EntityManagerFactory.class)
        static class ConditionalOnEntityManagerFactoryBean {
        }

    }

}
