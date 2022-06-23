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

    private static final ThreadLocal<Observation.Scope> scopes = new ThreadLocal<>();

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
        Observation.Scope scope = value.openScope();
        scopes.set(scope);
    }

    @Override
    public void reset() {
        Observation.Scope scope = scopes.get();
        Observation.Scope scopeFromObservationRegistry = observationRegistry.getCurrentObservationScope();
        if (scopeFromObservationRegistry != scope) {
            // TODO: Maybe we should throw an ISE
            LOG.warn("Scope from ObservationThreadLocalAccessor [" + scope
                    + "] is not the same as the one from ObservationRegistry [" + scopeFromObservationRegistry
                    + "]. You must have created additional scopes and forgotten to close them. Will close both of them");
            if (scopeFromObservationRegistry != null) {
                scopeFromObservationRegistry.close();
            }
        }
        if (scope != null) {
            scope.close();
            scopes.remove();
        }
    }

}
