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

import io.micrometer.observation.lang.Nullable;

/**
 * Default implementation of {@link ObservationRegistry}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
class SimpleObservationRegistry implements ObservationRegistry {

    private static final ThreadLocal<Observation> localObservation = new ThreadLocal<>();

    private final ObservationConfig observationConfig = new ObservationConfig();

    @Nullable
    @Override
    public Observation getCurrentObservation() {
        return localObservation.get();
    }

    @Override
    public void setCurrentObservation(@Nullable Observation current) {
        localObservation.set(current);
    }

    @Override
    public ObservationConfig observationConfig() {
        return this.observationConfig;
    }

    @Override
    public boolean isNoOp() {
        return ObservationRegistry.super.isNoOp();
    }

}
