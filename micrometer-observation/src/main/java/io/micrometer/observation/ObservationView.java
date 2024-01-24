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
import io.micrometer.observation.Observation.ContextView;

/**
 * Read only view on the {@link Observation}.
 *
 * @since 1.10.0
 */
public interface ObservationView {

    /**
     * Returns the {@link ObservationRegistry} attached to this observation.
     * @return corresponding observation registry
     * @since 1.10.10
     */
    default ObservationRegistry getObservationRegistry() {
        return ObservationRegistry.NOOP;
    }

    /**
     * Returns the {@link ContextView} attached to this observation.
     * @return corresponding context
     */
    ContextView getContextView();

    /**
     * Pops the last scope attached to this {@link ObservationView} in this thread.
     * @return scope for this {@link ObservationView}, {@code null} if there was no scope
     * @since 1.10.6
     */
    @Nullable
    default Observation.Scope getEnclosingScope() {
        return Observation.Scope.NOOP;
    }

}
