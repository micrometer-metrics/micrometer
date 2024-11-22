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

/**
 * Delegating {@link ObservationRegistry}.
 *
 * @author Christian Fredriksson
 * @since 1.14.2
 */
class DelegatingObservationRegistry implements ObservationRegistry {

    private ObservationRegistry delegate = ObservationRegistry.NOOP;

    @Override
    public Observation getCurrentObservation() {
        return delegate.getCurrentObservation();
    }

    @Override
    public Observation.Scope getCurrentObservationScope() {
        return delegate.getCurrentObservationScope();
    }

    @Override
    public void setCurrentObservationScope(Observation.Scope current) {
        delegate.setCurrentObservationScope(current);
    }

    @Override
    public ObservationConfig observationConfig() {
        return delegate.observationConfig();
    }

    @Override
    public boolean isNoop() {
        return delegate.isNoop();
    }

    void set(ObservationRegistry delegate) {
        this.delegate = delegate != null ? delegate : ObservationRegistry.NOOP;
    }

}
