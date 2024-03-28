/*
 * Copyright 2024 VMware, Inc.
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
 * No-op implementation of {@link Observation} that passes through to the parent
 * observation.
 *
 * @author Marcin Grzejszczak
 * @since 1.13.0
 */
final class PassthroughNoopObservation extends NoopObservation {

    private final @Nullable ObservationView parentObservation;

    PassthroughNoopObservation(@Nullable ObservationView parentObservation) {
        this.parentObservation = parentObservation;
    }

    @Override
    public ContextView getContextView() {
        if (this.parentObservation != null) {
            // we pass through to the parent
            return this.parentObservation.getContextView();
        }
        return super.getContextView();
    }

}
