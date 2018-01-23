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
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;

import static java.util.Collections.emptyList;

@Configuration
@NonNullApi
public class MeterRegistryPostProcessor implements BeanPostProcessor {
    private final MetricsProperties config;
    private final Collection<MeterBinder> binders;
    private final Collection<MeterRegistryCustomizer> customizers;

    @SuppressWarnings("ConstantConditions")
    MeterRegistryPostProcessor(MetricsProperties config,
                               ObjectProvider<Collection<MeterBinder>> binders,
                               ObjectProvider<Collection<MeterRegistryCustomizer>> customizers) {
        this.config = config;
        this.binders = binders.getIfAvailable() != null ? binders.getIfAvailable() : emptyList();
        this.customizers = customizers.getIfAvailable() != null ? customizers.getIfAvailable() : emptyList();
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof MeterRegistry) {
            MeterRegistry registry = (MeterRegistry) bean;

            // Customizers must be applied before binders, as they may add custom tags or alter
            // timer or summary configuration.
            customizers.forEach(c -> c.configureRegistry(registry));

            binders.forEach(b -> b.bindTo(registry));

            if (config.isUseGlobalRegistry() && registry != Metrics.globalRegistry) {
                Metrics.addRegistry(registry);
            }
        }

        return bean;
    }
}