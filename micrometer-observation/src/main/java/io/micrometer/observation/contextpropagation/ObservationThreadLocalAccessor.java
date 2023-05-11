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
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.observation.NullObservation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * A {@link ThreadLocalAccessor} to put and restore current {@link Observation}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public class ObservationThreadLocalAccessor implements ThreadLocalAccessor<Observation> {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ObservationThreadLocalAccessor.class);

    /**
     * Key under which Micrometer Observation is being registered.
     */
    public static final String KEY = "micrometer.observation";

    private ObservationRegistry observationRegistry = ObservationRegistry.create();

    public static ObservationThreadLocalAccessor instance;

    /**
     * Creates a new instance of this class and stores a static handle to it. Remember to
     * call {@link ContextRegistry#getInstance()} to load all accessors which will call
     * this constructor.
     */
    public ObservationThreadLocalAccessor() {
        instance = this;
        if (log.isDebugEnabled()) {
            log.debug("Remember to set the ObservationRegistry on this accessor!");
        }
    }

    /**
     * Creates a new instance of this class and stores a static handle to it.
     * @param observationRegistry observation registry
     */
    public ObservationThreadLocalAccessor(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    /**
     * Provide an {@link ObservationRegistry} to be used by
     * {@link ObservationThreadLocalAccessor}.
     * @param observationRegistry observation registry
     */
    public void setObservationRegistry(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    /**
     * Return the singleton instance of this {@link ObservationThreadLocalAccessor}.
     * @return instance
     */
    public static ObservationThreadLocalAccessor getInstance() {
        if (instance == null) {
            ContextRegistry.getInstance(); // this loads the instance
        }
        return instance;
    }

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
        if (value == null) {
            // Not closing a scope (we're not resetting)
            // Creating a new one with empty context and opens a new scope)
            // This scope will remember the previously created one to
            // which we will revert once "null scope" is closed
            new NullObservation(observationRegistry).start().openScope();
        }
        else {
            // Iterate over all handlers and open a new scope. The created scope will put
            // itself to TL.
            value.openScope();
        }
    }

    @Override
    public void reset() {
        Observation.Scope scope = observationRegistry.getCurrentObservationScope();
        if (scope != null) {
            scope.close();
        }
    }

    @Override
    public void restore(Observation value) {
        if (value == null) {
            log.warn("Restore called with <null> observation. This should not happen. Will fallback to reset");
            reset();
            return;
        }
        Observation.Scope scope = observationRegistry.getCurrentObservationScope();
        if (scope == null) {
            String msg = "There is no current scope in thread local. This situation should not happen";
            log.warn(msg);
            assert false : msg;
        }
        Observation.Scope previousObservationScope = scope.getPreviousObservationScope();
        if (previousObservationScope == null || value != previousObservationScope.getCurrentObservation()) {
            Observation previousObservation = previousObservationScope != null
                    ? previousObservationScope.getCurrentObservation() : null;
            String msg = "Observation <" + value
                    + "> to which we're restoring is not the same as the one set as this scope's parent observation <"
                    + previousObservation
                    + "> . Most likely a manually created Observation has a scope opened that was never closed. This may lead to thread polluting and memory leaks";
            log.warn(msg);
            assert false : msg;
        }
        reset();
    }

}
