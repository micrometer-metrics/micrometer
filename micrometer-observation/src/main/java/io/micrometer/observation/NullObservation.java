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

import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;

/**
 * A special {@link Observation} that should be only in special cases where clearing of
 * scopes is important. {@link NullObservation} is almost noop except for scoping related
 * methods.
 *
 * When {@link NullObservation} opens a scope, a "null scope" is created that has
 * reference to a previous, nullable {@link Observation.Scope}.
 *
 * @since 1.10.8
 * @see ObservationThreadLocalAccessor
 */
public class NullObservation extends SimpleObservation {

    public NullObservation(ObservationRegistry registry) {
        super("null", registry, new Context());
    }

    @Override
    void notifyOnObservationStarted() {
        // Don't want to call handlers
    }

    @Override
    void notifyOnError() {
        // Don't want to call handlers
    }

    @Override
    void notifyOnEvent(Event event) {
        // Don't want to call handlers
    }

    @Override
    void notifyOnScopeMakeCurrent() {
        // Don't want to call handlers
    }

    @Override
    void notifyOnScopeReset() {
        // Don't want to call handlers
    }

    @Override
    void notifyOnObservationStopped(Context context) {
        // Don't want to call handlers
    }

    @Override
    public Observation start() {
        return this;
    }

    @Override
    SimpleScope createScope() {
        return new NullScope(registry, this);
    }

}
