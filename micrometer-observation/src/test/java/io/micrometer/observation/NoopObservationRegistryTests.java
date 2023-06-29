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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class NoopObservationRegistryTests {

    @Test
    void should_respect_scopes() {
        ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
        then(observationRegistry.getCurrentObservation()).isNull();

        Observation observation = Observation.start("foo", observationRegistry);
        then(observation.isNoop()).isTrue();
        then(observationRegistry.getCurrentObservation()).isNull();

        try (Observation.Scope scope = observation.openScope()) {
            then(observationRegistry.getCurrentObservationScope()).isSameAs(scope);
            then(observationRegistry.getCurrentObservation()).isSameAs(observation);
            try (Observation.Scope scope2 = observation.openScope()) {
                then(observationRegistry.getCurrentObservationScope()).isSameAs(scope2);
                then(observationRegistry.getCurrentObservationScope().getPreviousObservationScope()).isSameAs(scope);
                then(observationRegistry.getCurrentObservation()).isSameAs(observation);
            }
            then(observationRegistry.getCurrentObservation()).isSameAs(observation);
        }

        then(observationRegistry.getCurrentObservation()).isNull();
    }

}
