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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Handler for an {@link Observation}. Hooks in to the lifecycle of an observation.
 * Example of handler implementations can create metrics, spans or logs.
 *
 * @param <T> type of context
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public interface ObservationHandler<T extends Observation.Context> {
    /**
     * Reacts to starting of an {@link Observation}.
     *
     * @param context a {@link Observation.Context}
     */
    default void onStart(T context) {
    }

    /**
     * Reacts to an error during an {@link Observation}.
     *
     * @param context a {@link Observation.Context}
     */
    default void onError(T context) {
    }

    /**
     * Reacts to opening of a {@link Observation.Scope}.
     *
     * @param context a {@link Observation.Context}
     */
    default void onScopeOpened(T context) {
    }

    /**
     * Reacts to closing of a {@link Observation.Scope}.
     *
     * @param context a {@link Observation.Context}
     */
    default void onScopeClosed(T context) {
    }

    /**
     * Reacts to stopping of an {@link Observation}.
     *
     * @param context a {@link Observation.Context}
     */
    default void onStop(T context) {
    }

    /**
     * Tells the registry whether this handler should be applied for a given {@link Observation.Context}.
     *
     * @param context a {@link Observation.Context}
     * @return {@code true} when this handler should be used
     */
    boolean supportsContext(Observation.Context context);

    /**
     * Handler wrapping other handlers.
     */
    @SuppressWarnings("rawtypes")
    interface CompositeObservationHandler extends ObservationHandler<Observation.Context> {
        /**
         * Returns the registered handlers.
         * @return registered handlers
         */
        List<ObservationHandler> getHandlers();
    }

    /**
     * Handler picking the first matching handler from the list.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    class FirstMatchingCompositeObservationHandler implements CompositeObservationHandler {
        private final List<ObservationHandler> handlers;

        /**
         * Creates a new instance of {@code FirstMatchingCompositeObservationHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        public FirstMatchingCompositeObservationHandler(ObservationHandler... handlers) {
            this(Arrays.asList(handlers));
        }

        /**
         * Creates a new instance of {@code FirstMatchingCompositeObservationHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        public FirstMatchingCompositeObservationHandler(List<ObservationHandler> handlers) {
            this.handlers = handlers;
        }

        @Override
        public List<ObservationHandler> getHandlers() {
            return this.handlers;
        }

        @Override
        public void onStart(Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onStart(context));
        }

        @Override
        public void onError(Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onError(context));
        }

        @Override
        public void onScopeOpened(Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onScopeOpened(context));
        }

        @Override
        public void onScopeClosed(Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onScopeClosed(context));
        }

        @Override
        public void onStop(Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onStop(context));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return getFirstApplicableHandler(context).isPresent();
        }

        private Optional<ObservationHandler> getFirstApplicableHandler(Observation.Context context) {
            return this.handlers.stream().filter(handler -> handler.supportsContext(context)).findFirst();
        }
    }

    /**
     * Handler picking all matching handlers from the list.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    class AllMatchingCompositeObservationHandler implements CompositeObservationHandler {
        private final List<ObservationHandler> handlers;

        /**
         * Creates a new instance of {@code AllMatchingCompositeObservationHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        public AllMatchingCompositeObservationHandler(ObservationHandler... handlers) {
            this(Arrays.asList(handlers));
        }

        /**
         * Creates a new instance of {@code AllMatchingCompositeObservationHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        public AllMatchingCompositeObservationHandler(List<ObservationHandler> handlers) {
            this.handlers = handlers;
        }

        @Override
        public List<ObservationHandler> getHandlers() {
            return this.handlers;
        }

        @Override
        public void onStart(Observation.Context context) {
            getAllApplicableHandlers(context).forEach(handler -> handler.onStart(context));
        }

        @Override
        public void onError(Observation.Context context) {
            getAllApplicableHandlers(context).forEach(handler -> handler.onError(context));
        }

        @Override
        public void onScopeOpened(Observation.Context context) {
            getAllApplicableHandlers(context).forEach(handler -> handler.onScopeOpened(context));
        }

        @Override
        public void onScopeClosed(Observation.Context context) {
            getAllApplicableHandlers(context).forEach(handler -> handler.onScopeClosed(context));
        }

        @Override
        public void onStop(Observation.Context context) {
            getAllApplicableHandlers(context).forEach(handler -> handler.onStop(context));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return getAllApplicableHandlers(context).findAny().isPresent();
        }

        private Stream<ObservationHandler> getAllApplicableHandlers(Observation.Context context) {
            return this.handlers.stream().filter(handler -> handler.supportsContext(context));
        }
    }
}
