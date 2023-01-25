/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.observation.contextpropagation;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * A {@link ThreadLocalAccessor} to put and restore current {@link Observation}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public class ObservationThreadLocalAccessor implements ThreadLocalAccessor<Observation> {

    private static final InternalLogger LOG = InternalLoggerFactory.getInstance(ObservationThreadLocalAccessor.class);

    /**
     * Key under which Micrometer Observation is being registered.
     */
    public static final String KEY = "micrometer.observation";

    private static final ObservationRegistry observationRegistry = ObservationRegistry.create();

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public Observation getValue() {
        return observationRegistry.getCurrentObservation();
    }

    @Override
    public void setValue(Observation value) {
        if (value == observationRegistry.getCurrentObservation()) {
            // A noop case where we don't want to override anything in thread locals. We DON'T want to call <openScope>.
            Observation.Scope scope = observationRegistry.getCurrentObservationScope();
            // We always want to open a new scope and close it later, so we create a scope that just reverts to a previous one
            observationRegistry.setCurrentObservationScope(new RevertToPrevious(observationRegistry, scope));
            return;
        }
        // Iterate over all handlers and open a new scope. The created scope will put itself to TL
        value.openScope();
    }

    @Override
    public void reset() {
        Observation.Scope scope = observationRegistry.getCurrentObservationScope();
        while (scope != null) {
            scope.close();
            scope = observationRegistry.getCurrentObservationScope();
        }
        observationRegistry.setCurrentObservationScope(null);
    }

    @Override
    public void restore(Observation value) {
        // TODO: Do we want to close the scope or just reset observation scopes?
        Observation.Scope scope = observationRegistry.getCurrentObservationScope();
        if (scope != null) {
            scope.close(); // scope will be removed from TL and previous scope will be restored to TL
        }
    }

    static class RevertToPrevious implements Observation.Scope {
        private final ObservationRegistry registry;
        private final Observation.Scope scope;

        RevertToPrevious(ObservationRegistry registry, Observation.Scope scope) {
            this.registry = registry;
            this.scope = scope;
        }


        @Override
        public Observation getCurrentObservation() {
            return this.scope.getCurrentObservation();
        }

        @Override
        public void close() {
            this.registry.setCurrentObservationScope(scope);
        }
    }

}
