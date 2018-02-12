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
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.lang.NonNullApi;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;

import java.util.Collection;

@NonNullApi
public class MeterRegistryPostProcessor implements BeanPostProcessor {
    private final ApplicationContext context;

    private volatile MeterRegistryConfigurer configurer;

    MeterRegistryPostProcessor(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        if (bean instanceof MeterRegistry) {
            getConfigurer().configure((MeterRegistry) bean);
        }
        return bean;
    }

    @SuppressWarnings("unchecked")
    private MeterRegistryConfigurer getConfigurer() {
        if (this.configurer == null) {
            this.configurer = new MeterRegistryConfigurer(beansOfType(MeterBinder.class),
                    beansOfType(MeterFilter.class),
                    (Collection<MeterRegistryCustomizer<?>>) (Object) beansOfType(
                            MeterRegistryCustomizer.class),
                    this.context.getBean(MetricsProperties.class).isUseGlobalRegistry());
        }
        return this.configurer;
    }

    private <T> Collection<T> beansOfType(Class<T> type) {
        return this.context.getBeansOfType(type).values();
    }
}
