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
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;

/**
 * Applies {@link MeterRegistryCustomizer customizers}, {@link MeterFilter filters},
 * {@link MeterBinder binders} and {@link Metrics#addRegistry
 * global registration} to {@link MeterRegistry meter registries}.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 */
class MeterRegistryConfigurer {

    private final ObjectProvider<List<MeterRegistryCustomizer<?>>> customizers;

    private final ObjectProvider<List<MeterFilter>> filters;

    private final ObjectProvider<List<MeterBinder>> binders;

    private final boolean addToGlobalRegistry;

    MeterRegistryConfigurer(ObjectProvider<List<MeterRegistryCustomizer<?>>> customizers,
                            ObjectProvider<List<MeterFilter>> filters, ObjectProvider<List<MeterBinder>> binders,
                            boolean addToGlobalRegistry) {
        this.customizers = customizers;
        this.filters = filters;
        this.binders = binders;
        this.addToGlobalRegistry = addToGlobalRegistry;
    }

    void configure(MeterRegistry registry) {
        // Customizers must be applied before binders, as they may add custom
        // tags or alter timer or summary configuration.
        customize(registry);
        addFilters(registry);
        addBinders(registry);
        if (this.addToGlobalRegistry && registry != Metrics.globalRegistry) {
            Metrics.addRegistry(registry);
        }
    }

    @SuppressWarnings("unchecked")
    private void customize(MeterRegistry registry) {
        // Customizers must be applied before binders, as they may add custom tags or alter
        // timer or summary configuration.
        for (MeterRegistryCustomizer customizer : getOrEmpty(this.customizers)) {
            try {
                customizer.customize(registry);
            } catch (ClassCastException ignored) {
                // This is essentially what LambdaSafe.callbacks(..).invoke(..) is doing
                // in Spring Boot 2, just trapping ClassCastExceptions since the generic type
                // has been erased by this point.
            }
        }
    }

    private void addFilters(MeterRegistry registry) {
        getOrEmpty(this.filters).forEach(registry.config()::meterFilter);
    }

    private void addBinders(MeterRegistry registry) {
        getOrEmpty(this.binders).forEach((binder) -> binder.bindTo(registry));
    }

    private <T> List<T> getOrEmpty(ObjectProvider<List<T>> listProvider) {
        List<T> list = listProvider.getIfAvailable();
        return list != null ? list : Collections.emptyList();
    }

}
