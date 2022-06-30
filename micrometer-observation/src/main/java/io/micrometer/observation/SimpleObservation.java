/*
 * Copyright 2022 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link Observation}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
class SimpleObservation implements Observation {

    private final ObservationRegistry registry;

    private final Context context;

    @SuppressWarnings("rawtypes")
    private final Collection<KeyValuesProvider> keyValuesProviders;

    @SuppressWarnings("rawtypes")
    private final Deque<ObservationHandler> handlers;

    private final Collection<ObservationFilter> filters;

    SimpleObservation(String name, ObservationRegistry registry, Context context) {
        this.registry = registry;
        this.context = context.setName(name);
        this.keyValuesProviders = registry.observationConfig().getKeyValuesProviders().stream()
                .filter(provider -> provider.supportsContext(this.context)).collect(Collectors.toList());
        this.handlers = registry.observationConfig().getObservationHandlers().stream()
                .filter(handler -> handler.supportsContext(this.context))
                .collect(Collectors.toCollection(ArrayDeque::new));
        this.filters = registry.observationConfig().getObservationFilters();
        registry.observationConfig().getObservationConventions().stream()
                .filter(observationConvention -> observationConvention.supportsContext(this.context)).findFirst()
                .ifPresent(convention -> {
                    this.keyValuesProviders.add(convention);
                    String newName = convention.getName();
                    if (StringUtils.isNotBlank(newName)) {
                        this.context.setName(newName);
                    }
                });
    }

    SimpleObservation(ObservationConvention<?> convention, ObservationRegistry registry, Context context) {
        this.context = context.setName(name(convention, context));
        this.registry = registry;
        this.keyValuesProviders = registry.observationConfig().getKeyValuesProviders().stream()
                .filter(provider -> provider.supportsContext(this.context)).collect(Collectors.toList());
        this.handlers = registry.observationConfig().getObservationHandlers().stream()
                .filter(handler -> handler.supportsContext(this.context))
                .collect(Collectors.toCollection(ArrayDeque::new));
        this.filters = registry.observationConfig().getObservationFilters();
        this.keyValuesProviders.add(convention);
    }

    private static String name(ObservationConvention<?> convention, Context context) {
        if (!convention.supportsContext(context)) {
            throw new IllegalStateException(
                    "Convention [" + convention + "] doesn't support context [" + context + "]");
        }
        String name = convention.getName();
        if (StringUtils.isNotBlank(name)) {
            return name;
        }
        return context.getName();
    }

    @Override
    public Observation contextualName(String contextualName) {
        this.context.setContextualName(contextualName);
        return this;
    }

    @Override
    public Observation lowCardinalityKeyValue(KeyValue keyValue) {
        this.context.addLowCardinalityKeyValue(keyValue);
        return this;
    }

    @Override
    public Observation highCardinalityKeyValue(KeyValue keyValue) {
        this.context.addHighCardinalityKeyValue(keyValue);
        return this;
    }

    @Override
    public Observation keyValuesProvider(KeyValuesProvider<?> keyValuesProvider) {
        if (keyValuesProvider.supportsContext(context)) {
            this.keyValuesProviders.add(keyValuesProvider);
        }
        return this;
    }

    @Override
    public Observation error(Throwable error) {
        this.context.setError(error);
        this.notifyOnError();
        return this;
    }

    @Override
    public Observation start() {
        this.notifyOnObservationStarted();
        return this;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void stop() {
        for (KeyValuesProvider keyValuesProvider : keyValuesProviders) {
            this.context.addLowCardinalityKeyValues(keyValuesProvider.getLowCardinalityKeyValues(context));
            this.context.addHighCardinalityKeyValues(keyValuesProvider.getHighCardinalityKeyValues(context));
        }
        Observation.Context modifiedContext = this.context;
        for (ObservationFilter filter : this.filters) {
            modifiedContext = filter.map(modifiedContext);
        }
        this.notifyOnObservationStopped(modifiedContext);
    }

    @Override
    public Scope openScope() {
        Scope scope = new SimpleScope(this.registry, this);
        this.notifyOnScopeOpened();
        return scope;
    }

    @Override
    public String toString() {
        return "{" + "name=" + this.context.getName() + "(" + this.context.getContextualName() + ")" + ", error="
                + this.context.getError() + ", context=" + this.context + '}';
    }

    @SuppressWarnings("unchecked")
    private void notifyOnObservationStarted() {
        this.handlers.forEach(handler -> handler.onStart(this.context));
    }

    @SuppressWarnings("unchecked")
    private void notifyOnError() {
        this.handlers.forEach(handler -> handler.onError(this.context));
    }

    @SuppressWarnings("unchecked")
    private void notifyOnScopeOpened() {
        this.handlers.forEach(handler -> handler.onScopeOpened(this.context));
    }

    @SuppressWarnings("unchecked")
    private void notifyOnScopeClosed() {
        // We're closing from end till the beginning - e.g. we opened scope with handlers
        // with ids 1,2,3 and we need to close the scope in order 3,2,1
        this.handlers.descendingIterator().forEachRemaining(handler -> handler.onScopeClosed(this.context));
    }

    @SuppressWarnings("unchecked")
    private void notifyOnObservationStopped(Observation.Context context) {
        // We're closing from end till the beginning - e.g. we started with handlers with
        // ids 1,2,3 and we need to call close on 3,2,1
        this.handlers.descendingIterator().forEachRemaining(handler -> handler.onStop(context));
    }

    static class SimpleScope implements Scope {

        private final ObservationRegistry registry;

        private final SimpleObservation currentObservation;

        @Nullable
        private final Observation.Scope previousObservationScope;

        SimpleScope(ObservationRegistry registry, SimpleObservation current) {
            this.registry = registry;
            this.currentObservation = current;
            this.previousObservationScope = registry.getCurrentObservationScope();
            this.registry.setCurrentObservationScope(this);
        }

        @Override
        public Observation getCurrentObservation() {
            return this.currentObservation;
        }

        @Override
        public void close() {
            this.registry.setCurrentObservationScope(previousObservationScope);
            this.currentObservation.notifyOnScopeClosed();
        }

    }

}
