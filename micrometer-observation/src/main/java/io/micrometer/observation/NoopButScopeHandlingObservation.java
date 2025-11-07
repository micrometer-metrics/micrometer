/*
 * Copyright 2025 VMware, Inc.
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
 * No-op implementation of {@link Observation} except scope opening so that we can disable
 * the instrumentation for certain Observations but keep context-propagation working.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 */
final class NoopButScopeHandlingObservation extends NoopObservation {

    static final Observation INSTANCE = new NoopButScopeHandlingObservation();

    @Override
    public Scope openScope() {
        return new SimpleObservation.SimpleScope(NoopObservationRegistry.FOR_SCOPES, this);
    }

}
