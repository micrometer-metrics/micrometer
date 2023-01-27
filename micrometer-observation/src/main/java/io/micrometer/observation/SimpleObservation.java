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

    @Nullable
    @SuppressWarnings("rawtypes")
    private ObservationConvention convention;

    @SuppressWarnings("rawtypes")
    private final Deque<ObservationHandler> handlers;

    private final Collection<ObservationFilter> filters;

    SimpleObservation(@Nullable String name, ObservationRegistry registry, Context context) {
        this.registry = registry;
        this.context = context;
        this.context.setName(name);
        this.convention = getConventionFromConfig(registry, context);
        this.handlers = getHandlersFromConfig(registry, context);
        this.filters = registry.observationConfig().getObservationFilters();
        setParentFromCurrentObservation();
    }

    SimpleObservation(ObservationConvention<? extends Context> convention, ObservationRegistry registry,
            Context context) {
        this.registry = registry;
        this.context = context;
        // name is set later in start()
        this.handlers = getHandlersFromConfig(registry, context);
        this.filters = registry.observationConfig().getObservationFilters();
        if (convention.supportsContext(context)) {
            this.convention = convention;
        }
        else {
            throw new IllegalStateException(
                    "Convention [" + convention + "] doesn't support context [" + context + "]");
        }
        setParentFromCurrentObservation();
    }

    private void setParentFromCurrentObservation() {
        Observation currentObservation = this.registry.getCurrentObservation();
        if (currentObservation != null) {
            this.context.setParentObservation(currentObservation);
        }
    }

    @Nullable
    private static ObservationConvention getConventionFromConfig(ObservationRegistry registry, Context context) {
        return registry.observationConfig().getObservationConventions().stream()
                .filter(convention -> convention.supportsContext(context)).findFirst().orElse(null);
    }

    private static Deque<ObservationHandler> getHandlersFromConfig(ObservationRegistry registry, Context context) {
        return registry.observationConfig().getObservationHandlers().stream()
                .filter(handler -> handler.supportsContext(context)).collect(Collectors.toCollection(ArrayDeque::new));
    }

    @Override
    public Observation contextualName(@Nullable String contextualName) {
        this.context.setContextualName(contextualName);
        return this;
    }

    @Override
    public Observation parentObservation(@Nullable Observation parentObservation) {
        this.context.setParentObservation(parentObservation);
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
    public Observation observationConvention(ObservationConvention<?> convention) {
        if (convention.supportsContext(context)) {
            this.convention = convention;
        }
        return this;
    }

    @Override
    public Observation error(Throwable error) {
        this.context.setError(error);
        notifyOnError();
        return this;
    }

    @Override
    public Observation event(Event event) {
        notifyOnEvent(event);
        return this;
    }

    @Override
    public Observation start() {
        if (this.convention != null) {
            this.context.addLowCardinalityKeyValues(convention.getLowCardinalityKeyValues(context));
            this.context.addHighCardinalityKeyValues(convention.getHighCardinalityKeyValues(context));

            String newName = convention.getName();
            if (StringUtils.isNotBlank(newName)) {
                this.context.setName(newName);
            }
        }

        notifyOnObservationStarted();
        return this;
    }

    @Override
    public Context getContext() {
        return this.context;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void stop() {
        if (this.convention != null) {
            this.context.addLowCardinalityKeyValues(convention.getLowCardinalityKeyValues(context));
            this.context.addHighCardinalityKeyValues(convention.getHighCardinalityKeyValues(context));

            String newContextualName = convention.getContextualName(context);
            if (StringUtils.isNotBlank(newContextualName)) {
                this.context.setContextualName(newContextualName);
            }
        }

        Observation.Context modifiedContext = this.context;
        for (ObservationFilter filter : this.filters) {
            modifiedContext = filter.map(modifiedContext);
        }

        notifyOnObservationStopped(modifiedContext);
    }

    @Override
    public Scope openScope() {
        Scope scope = new SimpleScope(this.registry, this);
        notifyOnScopeOpened();
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
    private void notifyOnEvent(Event event) {
        this.handlers.forEach(handler -> handler.onEvent(event, this.context));
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
