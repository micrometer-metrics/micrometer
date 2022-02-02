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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface ObservationHandler<T extends Observation.Context> {
    void onStart(Observation observation, T context);

    void onError(Observation observation, T context);

    default void onScopeOpened(Observation observation, T context) {
    }

    default void onScopeClosed(Observation observation, T context) {
    }

    void onStop(Observation observation, T context);

    boolean supportsContext(Observation.Context context);


    /**
     * Handler wrapping other handlers.
     */
    @SuppressWarnings("rawtypes")
    interface CompositeObservationHandler extends ObservationHandler<Observation.Context> {
        /**
         * Returns the registered recording handlers.
         * @return registered handlers
         */
        List<ObservationHandler> getHandlers();
    }

    /**
     * Handler picking the first matching recording handler from the list.
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
        public void onStart(Observation observation, Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onStart(observation, context));
        }

        @Override
        public void onError(Observation observation, Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onError(observation, context));
        }

        @Override
        public void onScopeOpened(Observation observation, Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onScopeOpened(observation, context));
        }

        @Override
        public void onScopeClosed(Observation observation, Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onScopeClosed(observation, context));
        }

        @Override
        public void onStop(Observation observation, Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onStop(observation, context));
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
     * Handler picking all matching recording handlers from the list.
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
        public void onStart(Observation observation, Observation.Context context) {
            getAllApplicableHandlers(context).forEach(handler -> handler.onStart(observation, context));
        }

        @Override
        public void onError(Observation observation, Observation.Context context) {
            getAllApplicableHandlers(context).forEach(handler -> handler.onError(observation, context));
        }

        @Override
        public void onScopeOpened(Observation observation, Observation.Context context) {
            getAllApplicableHandlers(context).forEach(handler -> handler.onScopeOpened(observation, context));
        }

        @Override
        public void onScopeClosed(Observation observation, Observation.Context context) {
            getAllApplicableHandlers(context).forEach(handler -> handler.onScopeClosed(observation, context));
        }

        @Override
        public void onStop(Observation observation, Observation.Context context) {
            getAllApplicableHandlers(context).forEach(handler -> handler.onStop(observation, context));
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
