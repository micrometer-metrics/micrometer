/*
 * Copyright 2023 VMware, Inc.
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
 * A special {@link Observation} that should be used only in special cases where clearing
 * of scopes is important. It will not call any handler methods except for scope related
 * ones.
 *
 * @since 1.10.8
 * @see ObservationThreadLocalAccessor
 */
public class NullObservation extends SimpleObservation {

    public NullObservation(ObservationRegistry registry) {
        super("null", registry, new NullContext());
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

    /**
     * A special {@link Observation.Context} that should be used only in
     * {@link NullObservation} in special cases where clearing of scopes is important. Its
     * only purpose is to make scenarios through {@link NullObservation} distinguishable
     * from "normal" {@link Observation Observations}.
     *
     * @since 1.14.0
     */
    public static class NullContext extends Context {

    }

}
