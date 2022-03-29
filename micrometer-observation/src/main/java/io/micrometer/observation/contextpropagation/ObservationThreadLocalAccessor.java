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

import io.micrometer.contextpropagation.ContextContainer;
import io.micrometer.contextpropagation.Namespace;
import io.micrometer.contextpropagation.NamespaceAccessor;
import io.micrometer.contextpropagation.Store;
import io.micrometer.contextpropagation.ThreadLocalAccessor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * A {@link ThreadLocalAccessor} to put and restore current {@link Observation}.
 */
public class ObservationThreadLocalAccessor implements ThreadLocalAccessor {

    private static final String KEY = "micrometer.observation";

    /**
     * Namespace for observations.
     */
    public static final Namespace OBSERVATION = Namespace.of(KEY);

    private final NamespaceAccessor<ObservationStore> namespaceAccessor = new NamespaceAccessor<>(OBSERVATION);

    private static final ObservationRegistry observationRegistry = ObservationRegistry.create();

    @Override
    public void captureValues(ContextContainer container) {
        Observation value = observationRegistry.getCurrentObservation();
        if (value != null) {
            this.namespaceAccessor.putStore(container, new ObservationStore(value));
        }
    }

    @Override
    public void restoreValues(ContextContainer container) {
        if (this.namespaceAccessor.isPresent(container)) {
            ObservationStore store = this.namespaceAccessor.getStore(container);
            Observation observation = store.getObservation();
            Observation.Scope scope = observation.openScope();
            store.putScope(scope);
        }
    }

    @Override
    public void resetValues(ContextContainer container) {
        this.namespaceAccessor.getRequiredStore(container).close();
    }

    @Override
    public Namespace getNamespace() {
        return OBSERVATION;
    }

    static final class ObservationStore implements Store, AutoCloseable {
        final Observation observation;
        Observation.Scope scope;

        ObservationStore(Observation observation) {
            this.observation = observation;
        }

        Observation getObservation() {
            return this.observation;
        }

        void putScope(Observation.Scope scope) {
            this.scope = scope;
        }

        @Override
        public void close() {
            if (this.scope != null) {
                this.scope.close();
            }
        }
    }
}
