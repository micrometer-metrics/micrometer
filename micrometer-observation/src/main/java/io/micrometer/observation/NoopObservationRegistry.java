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
 * No-op implementation of {@link ObservationRegistry} so that we can disable the
 * instrumentation logic.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
final class NoopObservationRegistry implements ObservationRegistry {

    /**
     * Instance of {@link NoopObservationRegistry}.
     */
    static final NoopObservationRegistry INSTANCE = new NoopObservationRegistry();

    private final ObservationConfig observationConfig = NoopObservationConfig.INSTANCE;

    private NoopObservationRegistry() {
    }

    @Override
    public Observation getCurrentObservation() {
        return NoopObservation.INSTANCE;
    }

    @Override
    public Observation.Scope getCurrentObservationScope() {
        return NoopObservation.NoopScope.INSTANCE;
    }

    @Override
    public void setCurrentObservationScope(Observation.Scope current) {

    }

    @Override
    public ObservationConfig observationConfig() {
        return this.observationConfig;
    }

}
