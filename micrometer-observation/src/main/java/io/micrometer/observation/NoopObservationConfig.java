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

import java.util.Collection;
import java.util.Collections;

/**
 * No-op implementation of {@link ObservationRegistry.ObservationConfig} so that we can
 * disable the instrumentation logic.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
final class NoopObservationConfig extends ObservationRegistry.ObservationConfig {

    /**
     * Instance of {@link NoopObservationConfig}.
     */
    static final NoopObservationConfig INSTANCE = new NoopObservationConfig();

    private NoopObservationConfig() {
    }

    @Override
    public ObservationRegistry.ObservationConfig observationHandler(ObservationHandler<?> handler) {
        return this;
    }

    @Override
    public ObservationRegistry.ObservationConfig observationPredicate(ObservationPredicate predicate) {
        return this;
    }

    @Override
    public boolean isObservationEnabled(String name, Observation.Context context) {
        return false;
    }

    @Override
    Collection<ObservationHandler<?>> getObservationHandlers() {
        return Collections.emptyList();
    }

}
