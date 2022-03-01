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
package io.micrometer.core.instrument.contextpropagation;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.observation.Observation;
import io.micrometer.core.instrument.observation.ObservationRegistry;
import io.micrometer.contextpropagation.ContextContainer;
import io.micrometer.contextpropagation.ThreadLocalAccessor;

/**
 * A {@link ThreadLocalAccessor} to put and restore current {@link Observation}.
 */
public class ObservationThreadLocalAccessor implements ThreadLocalAccessor {

    private static final String KEY = Observation.class.getName();
    private static final String SCOPE_KEY = Observation.Scope.class.getName();

    private static final ObservationRegistry observationRegistry = Metrics.globalRegistry;

    @Override
    public void captureValues(ContextContainer container) {
        Observation value = observationRegistry.getCurrentObservation();
        if (value != null) {
            container.put(KEY, value);
        }
    }

    @Override
    public void restoreValues(ContextContainer container) {
        if (container.containsKey(KEY)) {
            Observation observation = container.get(KEY);
            Observation.Scope scope = observation.openScope();
            container.put(SCOPE_KEY, scope);
        }
    }

    @Override
    public void resetValues(ContextContainer container) {
        Observation.Scope scope = container.get(SCOPE_KEY);
        if (scope != null) {
            scope.close();
        }
    }

}
