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
package io.micrometer.observation.tck;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.assertj.core.api.AssertProvider;

/**
 * Implementation of {@link ObservationRegistry} used for testing.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public final class TestObservationRegistry
        implements ObservationRegistry, AssertProvider<TestObservationRegistryAssert> {

    private final ObservationRegistry delegate = ObservationRegistry.create();

    private final StoringObservationHandler handler = new StoringObservationHandler();

    private TestObservationRegistry() {
        observationConfig().observationHandler(this.handler).observationHandler(new ObservationValidator());
    }

    /**
     * Crates a new instance of mock observation registry.
     * @return mock instance of observation registry
     */
    public static TestObservationRegistry create() {
        return new TestObservationRegistry();
    }

    @Override
    public Observation getCurrentObservation() {
        return this.delegate.getCurrentObservation();
    }

    @Override
    public Observation.Scope getCurrentObservationScope() {
        return this.delegate.getCurrentObservationScope();
    }

    @Override
    public void setCurrentObservationScope(Observation.Scope current) {
        this.delegate.setCurrentObservationScope(current);
    }

    @Override
    public ObservationConfig observationConfig() {
        return this.delegate.observationConfig();
    }

    Queue<TestObservationContext> getContexts() {
        return this.handler.contexts;
    }

    /**
     * Clears the stored {@link Observation.Context}.
     * @since 1.11.0
     */
    public void clear() {
        getContexts().clear();
    }

    /**
     * Return an assert for AspectJ.
     * @return an AspectJ assert
     * @deprecated to prevent accidental use. Prefer standard AssertJ
     * {@code assertThat(observationRegistry)...} calls instead.
     */
    @Deprecated
    @Override
    public TestObservationRegistryAssert assertThat() {
        return TestObservationRegistryAssert.assertThat(this);
    }

    private static class StoringObservationHandler implements ObservationHandler<Observation.Context> {

        final Queue<TestObservationContext> contexts = new ConcurrentLinkedQueue<>();

        @Override
        public void onStart(Observation.Context context) {
            this.contexts.add(new TestObservationContext(context).setObservationStarted(true));
        }

        @Override
        public void onStop(Observation.Context context) {
            this.contexts.stream()
                .filter(testContext -> testContext.getContext() == context)
                .findFirst()
                .ifPresent(testContext -> testContext.setObservationStopped(true));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public void onEvent(Observation.Event event, Observation.Context context) {
            this.contexts.stream()
                .filter(testContext -> testContext.getContext() == context)
                .findFirst()
                .ifPresent(testContext -> testContext.addEvent(event));
        }

    }

    static class TestObservationContext {

        private final Observation.Context context;

        private boolean observationStarted;

        private boolean observationStopped;

        private final Set<Observation.Event> contextEvents = new HashSet<>();

        TestObservationContext(Observation.Context context) {
            this.context = context;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestObservationContext that = (TestObservationContext) o;
            return Objects.equals(this.context, that.context);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.context);
        }

        TestObservationContext setObservationStarted(boolean observationStarted) {
            this.observationStarted = observationStarted;
            return this;
        }

        TestObservationContext setObservationStopped(boolean observationStopped) {
            this.observationStopped = observationStopped;
            return this;
        }

        /**
         * Was {@link Observation} started?
         * @return {@code true} if observation was started
         */
        boolean isObservationStarted() {
            return this.observationStarted;
        }

        /**
         * Was {@link Observation} stopped?
         * @return {@code true} if observation was stopped
         */
        boolean isObservationStopped() {
            return this.observationStopped;
        }

        Observation.Context getContext() {
            return this.context;
        }

        /**
         * Stores an {@link Observation.Event} in this context.
         * @param event the event to store
         */
        void addEvent(Observation.Event event) {
            this.contextEvents.add(event);
        }

        /**
         * Check if an {@link Observation.Event} with the given name was stored in this
         * context.
         * @param name name of the event to check
         * @return {@code true} if an event was stored under the given name
         */
        boolean hasEvent(String name) {
            return this.contextEvents.stream().anyMatch(event -> event.getName().equals(name));
        }

        /**
         * Check if an {@link Observation.Event} with the given name and contextual name
         * was stored in this context.
         * @param name name of the event to check
         * @param contextualName contextual name of the event to check
         * @return {@code true} if an event was stored under the given name and contextual
         * name
         */
        boolean hasEvent(String name, String contextualName) {
            return this.contextEvents.stream()
                .anyMatch(event -> event.getName().equals(name) && event.getContextualName().equals(contextualName));
        }

    }

}
