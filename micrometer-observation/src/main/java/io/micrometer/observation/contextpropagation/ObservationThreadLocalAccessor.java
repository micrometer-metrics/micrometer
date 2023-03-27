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

    /**
     * Key under which Micrometer Observation is being registered.
     */
    public static final String KEY = "micrometer.observation";

    private static final String SCOPE_KEY = KEY + ".scope";

    private static final ObservationRegistry observationRegistry = ObservationRegistry.create();

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public Observation getValue() {
        Observation.Scope scope = observationRegistry.getCurrentObservationScope();
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
        Observation.Scope scope = observationRegistry.getCurrentObservationScope();
        if (scope != null) {
            scope.reset();
        }
    }

    @Override
    public void restore(Observation value) {
        reset();
        Observation.Scope observationScope = value.getContext().get(SCOPE_KEY);
        if (observationScope != null) {
            observationScope.makeCurrent();
        } else {
            // shouldn't happen
        }
    }

}
