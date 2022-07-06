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

import io.micrometer.common.lang.Nullable;

/**
 * Default implementation of {@link ObservationRegistry}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
class SimpleObservationRegistry implements ObservationRegistry {

    private static final ThreadLocal<Observation.Scope> localObservationScope = new ThreadLocal<>();

    private final ObservationConfig observationConfig = new ObservationConfig();

    @Nullable
    @Override
    public Observation getCurrentObservation() {
        Observation.Scope scope = localObservationScope.get();
        if (scope != null) {
            return scope.getCurrentObservation();
        }
        return null;
    }

    @Override
    public Observation.Scope getCurrentObservationScope() {
        return localObservationScope.get();
    }

    @Override
    public void setCurrentObservationScope(Observation.Scope current) {
        localObservationScope.set(current);
    }

    @Override
    public ObservationConfig observationConfig() {
        return this.observationConfig;
    }

    @Override
    public boolean isNoop() {
        return ObservationRegistry.super.isNoop() || observationConfig().getObservationHandlers().isEmpty();
    }

}
