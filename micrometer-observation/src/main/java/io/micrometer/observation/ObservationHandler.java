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

import io.micrometer.observation.Observation.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Handler for an {@link Observation}. Hooks in to the lifecycle of an observation.
 * Example of handler implementations can create metrics, spans or logs.
 *
 * @param <T> type of context
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface ObservationHandler<T extends Observation.Context> {

    /**
     * Reacts to starting of an {@link Observation}.
     * @param context an {@link Observation.Context}
     */
    default void onStart(T context) {
    }

    /**
     * Reacts to an error during an {@link Observation}.
     * @param context an {@link Observation.Context}
     */
    default void onError(T context) {
    }

    /**
     * Reacts to arbitrary {@link Observation.Event}.
     * @param event the {@link Observation.Event} that was signaled
     * @param context an {@link Observation.Context}
     */
    default void onEvent(Observation.Event event, T context) {
    }

    /**
     * Reacts to opening of an {@link Observation.Scope}.
     * @param context an {@link Observation.Context}
     */
    default void onScopeOpened(T context) {
    }

    /**
     * Reacts to closing of an {@link Observation.Scope}.
     * @param context an {@link Observation.Context}
     */
    default void onScopeClosed(T context) {
    }

    /**
     * Reacts to resetting of scopes. If your handler uses a {@link ThreadLocal} value,
     * this method should clear that {@link ThreadLocal} or any other scoped variable.
     * @param context an {@link Observation.Context}
     * @since 1.10.4
     */
    default void onScopeReset(T context) {
    }

    /**
     * Reacts to stopping of an {@link Observation}.
     * @param context an {@link Observation.Context}
     */
    default void onStop(T context) {
    }

    /**
     * Tells the registry whether this handler should be applied for a given
     * {@link Observation.Context}.
     * @param context an {@link Observation.Context}
     * @return {@code true} when this handler should be used
     */
    boolean supportsContext(Observation.Context context);

    /**
     * Handler wrapping other handlers.
     */
    interface CompositeObservationHandler extends ObservationHandler<Observation.Context> {

        /**
         * Returns the registered handlers.
         * @return registered handlers
         */
        List<ObservationHandler<Observation.Context>> getHandlers();

    }

    /**
     * Handler picking the first matching handler from the list.
     */
    class FirstMatchingCompositeObservationHandler implements CompositeObservationHandler {

        private final List<ObservationHandler<Observation.Context>> handlers;

        /**
         * Creates a new instance of {@code FirstMatchingCompositeObservationHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        @SafeVarargs
        public FirstMatchingCompositeObservationHandler(ObservationHandler<? extends Observation.Context>... handlers) {
            this(Arrays.asList(handlers));
        }

        /**
         * Creates a new instance of {@code FirstMatchingCompositeObservationHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        @SuppressWarnings("unchecked")
        public FirstMatchingCompositeObservationHandler(
                List<? extends ObservationHandler<? extends Observation.Context>> handlers) {
            List<ObservationHandler<Observation.Context>> castedHandlers = new ArrayList<>(handlers.size());
            for (ObservationHandler<? extends Observation.Context> handler : handlers) {
                castedHandlers.add((ObservationHandler<Observation.Context>) handler);
            }
            this.handlers = castedHandlers;
        }

        @Override
        public List<ObservationHandler<Observation.Context>> getHandlers() {
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
        public void onEvent(Observation.Event event, Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onEvent(event, context));
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
        public void onScopeReset(Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onScopeReset(context));
        }

        @Override
        public void onStop(Observation.Context context) {
            getFirstApplicableHandler(context).ifPresent(handler -> handler.onStop(context));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return getFirstApplicableHandler(context).isPresent();
        }

        private Optional<ObservationHandler<Observation.Context>> getFirstApplicableHandler(
                Observation.Context context) {
            for (ObservationHandler<Context> handler : this.handlers) {
                if (handler.supportsContext(context)) {
                    return Optional.of(handler);
                }
            }
            return Optional.empty();
        }

    }

    /**
     * Handler picking all matching handlers from the list.
     */
    class AllMatchingCompositeObservationHandler implements CompositeObservationHandler {

        private final List<ObservationHandler<Observation.Context>> handlers;

        /**
         * Creates a new instance of {@code AllMatchingCompositeObservationHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        @SafeVarargs
        public AllMatchingCompositeObservationHandler(ObservationHandler<? extends Observation.Context>... handlers) {
            this(Arrays.asList(handlers));
        }

        /**
         * Creates a new instance of {@code AllMatchingCompositeObservationHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        @SuppressWarnings("unchecked")
        public AllMatchingCompositeObservationHandler(
                List<? extends ObservationHandler<? extends Observation.Context>> handlers) {
            List<ObservationHandler<Observation.Context>> castedHandlers = new ArrayList<>(handlers.size());
            for (ObservationHandler<? extends Observation.Context> handler : handlers) {
                castedHandlers.add((ObservationHandler<Observation.Context>) handler);
            }
            this.handlers = castedHandlers;
        }

        @Override
        public List<ObservationHandler<Observation.Context>> getHandlers() {
            return this.handlers;
        }

        @Override
        public void onStart(Observation.Context context) {
            forEachApplicableHandler(context, handler -> handler.onStart(context));
        }

        @Override
        public void onError(Observation.Context context) {
            forEachApplicableHandler(context, handler -> handler.onError(context));
        }

        @Override
        public void onEvent(Observation.Event event, Observation.Context context) {
            forEachApplicableHandler(context, handler -> handler.onEvent(event, context));
        }

        @Override
        public void onScopeOpened(Observation.Context context) {
            forEachApplicableHandler(context, handler -> handler.onScopeOpened(context));
        }

        @Override
        public void onScopeClosed(Observation.Context context) {
            forEachApplicableHandler(context, handler -> handler.onScopeClosed(context));
        }

        @Override
        public void onScopeReset(Observation.Context context) {
            forEachApplicableHandler(context, handler -> handler.onScopeReset(context));
        }

        @Override
        public void onStop(Observation.Context context) {
            forEachApplicableHandler(context, handler -> handler.onStop(context));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            for (ObservationHandler<Context> handler : this.handlers) {
                if (handler.supportsContext(context)) {
                    return true;
                }
            }
            return false;
        }

        private void forEachApplicableHandler(Observation.Context context,
                Consumer<? super ObservationHandler<Observation.Context>> action) {
            for (ObservationHandler<Context> handler : this.handlers) {
                if (handler.supportsContext(context)) {
                    action.accept(handler);
                }
            }
        }

    }

}
