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
package io.micrometer.api.instrument.observation;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.stream.Collectors;

import io.micrometer.api.instrument.Tag;
import io.micrometer.api.lang.Nullable;

/**
 * Default implementation of {@link Observation}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 *
 * @since 2.0.0
 */
class SimpleObservation implements Observation {
    private final ObservationRegistry registry;
    private final Context context;
    @SuppressWarnings("rawtypes")
    private final Collection<TagsProvider> tagsProviders;
    @SuppressWarnings("rawtypes")
    private final Deque<ObservationHandler> handlers;

    // package private so only instantiated by us
    SimpleObservation(String name, ObservationRegistry registry, Context context) {
        this.registry = registry;
        this.context = context.setName(name);
        this.tagsProviders = registry.observationConfig().getTagsProviders().stream()
                .filter(tagsProvider -> tagsProvider.supportsContext(this.context))
                .collect(Collectors.toList());
        this.handlers = registry.observationConfig().getObservationHandlers().stream()
                .filter(handler -> handler.supportsContext(this.context))
                .collect(Collectors.toCollection(ArrayDeque::new));
    }

    @Override
    public Observation contextualName(String contextualName) {
        this.context.setContextualName(contextualName);
        return this;
    }

    @Override
    public Observation lowCardinalityTag(Tag tag) {
        this.context.addLowCardinalityTag(tag);
        return this;
    }

    @Override
    public Observation highCardinalityTag(Tag tag) {
        this.context.addHighCardinalityTag(tag);
        return this;
    }

    @Override
    public Observation tagsProvider(TagsProvider<?> tagsProvider) {
        if (tagsProvider.supportsContext(context)) {
            this.tagsProviders.add(tagsProvider);
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void stop() {
        for (TagsProvider tagsProvider : tagsProviders) {
            this.context.addLowCardinalityTags(tagsProvider.getLowCardinalityTags(context));
            this.context.addHighCardinalityTags(tagsProvider.getHighCardinalityTags(context));
        }
        this.notifyOnObservationStopped();
    }

    @Override
    public Scope openScope() {
        Scope scope = new SimpleScope(this.registry, this);
        this.notifyOnScopeOpened();
        return scope;
    }

    @Override
    public String toString() {
        return "{"
                + "name=" + this.context.getName() + "(" + this.context.getContextualName() + ")"
                + ", error=" + this.context.getError()
                + ", context=" + this.context
                + '}';
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
        // We're closing from end till the beginning - e.g. we opened scope with handlers with ids 1,2,3 and we need to close the scope in order 3,2,1
        this.handlers.descendingIterator().forEachRemaining(handler -> handler.onScopeClosed(this.context));
    }

    @SuppressWarnings("unchecked")
    private void notifyOnObservationStopped() {
        // We're closing from end till the beginning - e.g. we started with handlers with ids 1,2,3 and we need to call close on 3,2,1
        this.handlers.descendingIterator().forEachRemaining(handler -> handler.onStop(this.context));
    }

    static class SimpleScope implements Scope {
        private final ObservationRegistry registry;
        private final SimpleObservation currentObservation;
        @Nullable private final Observation previousObservation;

        SimpleScope(ObservationRegistry registry, SimpleObservation current) {
            this.registry = registry;
            this.currentObservation = current;
            this.previousObservation = registry.getCurrentObservation();
            this.registry.setCurrentObservation(current);
        }

        @Override
        public Observation getCurrentObservation() {
            return this.currentObservation;
        }

        @Override
        public void close() {
            this.registry.setCurrentObservation(previousObservation);
            this.currentObservation.notifyOnScopeClosed();
        }
    }
}
