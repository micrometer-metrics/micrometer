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

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.Objects;

/**
 * A {@link ThreadLocalAccessor} to put and restore current {@link Observation}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public class ObservationThreadLocalAccessor implements ThreadLocalAccessor<Observation> {

    /**
     * Key under which Micrometer Observation is being registered.
     */
    public static final String KEY = "micrometer.observation";

    private static ObservationThreadLocalAccessor instance;

    private static final String SCOPE_KEY = KEY + ".scope";

    private ObservationRegistry observationRegistry;

    public ObservationThreadLocalAccessor() {
        instance = this;
    }

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public Observation getValue() {
        Observation.Scope scope = observationRegistry().getCurrentObservationScope();
        if (scope != null) {
            Observation observation = scope.getCurrentObservation();
            observation.getContext().put(SCOPE_KEY, scope);
            return observation;
        }
        else {
            return null;
        }
    }

    @Override
    public void setValue(Observation value) {
        // Iterate over all handlers and open a new scope. The created scope will put
        // itself to TL.
        value.openScope();
    }

    @Override
    public void reset() {
        Observation.Scope scope = observationRegistry().getCurrentObservationScope();
        if (scope != null) {
            scope.reset();
        }
    }

    private ObservationRegistry observationRegistry() {
        return Objects.requireNonNull(observationRegistry,
                "You must override the default ObservationRegistry, otherwise your configured handlers will never be called. First ensure that <ContextRegistry.getInstance()> was called, then you can set the ObservationRegistry by calling <ObservationThreadLocalAccessor.getInstance().setObservationRegistry(...)>.");
    }

    @Override
    public void restore(Observation value) {
        reset();
        Observation.Scope observationScope = value.getContext().get(SCOPE_KEY);
        if (observationScope != null) {
            observationScope.close(); // We close the previous scope -
            // it will put its parent as current and call all handlers
        }
        setValue(value); // We open the previous scope again, however this time in TL
        // we have the whole hierarchy of scopes re-attached via handlers
    }

    /**
     * Returns the instance of {@link ObservationThreadLocalAccessor}.
     * @return instance
     */
    public static ObservationThreadLocalAccessor getInstance() {
        ContextRegistry.getInstance(); // we're ensuring that the SPI mechanism got called
                                       // and an instance of OTLA was created
        return instance;
    }

    public void setObservationRegistry(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

}
