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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.*;
import org.springframework.beans.factory.support.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import static java.util.Arrays.stream;
import static org.springframework.util.ObjectUtils.isEmpty;

/**
 * {@link BeanFactoryPostProcessor} to register a {@link CompositeMeterRegistry} when
 * necessary.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 */
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class CompositeMeterRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, BeanFactoryAware {

    private static final String COMPOSITE_BEAN_NAME = "compositeMeterRegistry";

    private ConfigurableListableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof ConfigurableListableBeanFactory) {
            this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (this.beanFactory == null) {
            return;
        }
        String[] registryBeans = this.beanFactory.getBeanNamesForType(MeterRegistry.class, true, false);
        registerCompositeIfNecessary(registry, registryBeans);
    }

    private void registerCompositeIfNecessary(BeanDefinitionRegistry registry,
                                              String[] registryBeans) {
        if (isEmpty(registryBeans)) {
            registerNoOpMeterRegistry(registry);
        }
        if (registryBeans.length > 1 && !hasPrimaryDefinition(registryBeans)) {
            registerPrimaryCompositeMeterRegistry(registry, registryBeans);
        }
    }

    private boolean hasPrimaryDefinition(String[] registryBeans) {
        return stream(registryBeans).map(this.beanFactory::getBeanDefinition)
            .anyMatch(BeanDefinition::isPrimary);
    }

    private void registerNoOpMeterRegistry(BeanDefinitionRegistry registry) {
        // If there are no meter registries configured, we register an empty composite
        // that effectively no-ops metrics instrumentation throughout the app.
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(CompositeMeterRegistry.class);
        registry.registerBeanDefinition(COMPOSITE_BEAN_NAME, definition);
    }

    private void registerPrimaryCompositeMeterRegistry(BeanDefinitionRegistry registry,
                                                       String[] registryBeans) {
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(CompositeMeterRegistry.class);
        definition.setPrimary(true);
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        ConstructorArgumentValues arguments = new ConstructorArgumentValues();
        arguments.addIndexedArgumentValue(1, getBeanReferences(registryBeans));
        definition.setConstructorArgumentValues(arguments);

        registry.registerBeanDefinition(COMPOSITE_BEAN_NAME, definition);
    }

    private ManagedList<RuntimeBeanReference> getBeanReferences(String[] names) {
        ManagedList<RuntimeBeanReference> references = new ManagedList<>(names.length);
        stream(names).map(RuntimeBeanReference::new).forEach(references::add);
        return references;
    }
}