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

class ObservationNoopTests {

    @Test
    @SuppressWarnings("NullAway")
    void shouldRespectScopesIfDisabledByPredicate() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().getObservationHandlers().add(new ObservationTextPublisher());
        observationRegistry.observationConfig().observationPredicate((name, context) -> false);
        then(observationRegistry.getCurrentObservation()).isNull();

        Observation observation = Observation.start("foo", observationRegistry);
        then(observation.isNoop()).isTrue();
        then(observation).isSameAs(NoopButScopeHandlingObservation.INSTANCE);
        then(observationRegistry.getCurrentObservation()).isNull();

        try (Observation.Scope scope1 = observation.openScope()) {
            then(scope1.isNoop()).isFalse();
            then(observationRegistry.getCurrentObservationScope()).isSameAs(scope1);
            then(observationRegistry.getCurrentObservationScope().getPreviousObservationScope()).isNull();
            then(observationRegistry.getCurrentObservation()).isSameAs(observation);
            try (Observation.Scope scope2 = observation.openScope()) {
                then(scope2.isNoop()).isFalse();
                then(observationRegistry.getCurrentObservationScope()).isSameAs(scope2);
                then(observationRegistry.getCurrentObservationScope().getPreviousObservationScope()).isSameAs(scope1);
                then(observationRegistry.getCurrentObservation()).isSameAs(observation);
            }
            then(observationRegistry.getCurrentObservation()).isSameAs(observation);
        }

        then(observationRegistry.getCurrentObservation()).isNull();
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldNotRespectScopesIfNullRegistryIsUsed() {
        Observation observation = Observation.start("foo", null);
        then(observation.isNoop()).isTrue();
        then(observation).isSameAs(Observation.NOOP);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldNotRespectScopesIfNoopRegistryIsUsed() {
        ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
        then(observationRegistry.getCurrentObservation()).isNull();

        Observation observation = Observation.start("foo", observationRegistry);
        then(observation.isNoop()).isTrue();
        then(observation).isSameAs(Observation.NOOP);
        then(observationRegistry.getCurrentObservation()).isNull();

        try (Observation.Scope scope1 = observation.openScope()) {
            then(scope1.isNoop()).isTrue();
            then(scope1).isSameAs(Observation.Scope.NOOP);
            then(observationRegistry.getCurrentObservationScope()).isNull();
            then(observationRegistry.getCurrentObservation()).isNull();
            try (Observation.Scope scope2 = observation.openScope()) {
                then(scope2.isNoop()).isTrue();
                then(scope2).isSameAs(Observation.Scope.NOOP);
                then(observationRegistry.getCurrentObservationScope()).isNull();
                then(observationRegistry.getCurrentObservation()).isNull();
            }
            then(observationRegistry.getCurrentObservation()).isNull();
        }

        then(observationRegistry.getCurrentObservation()).isNull();
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldNotRespectScopesIfNoHandlersAreRegistered() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        then(observationRegistry.getCurrentObservation()).isNull();

        Observation observation = Observation.start("foo", observationRegistry);
        then(observation.isNoop()).isTrue();
        then(observation).isSameAs(Observation.NOOP);
        then(observationRegistry.getCurrentObservation()).isNull();

        try (Observation.Scope scope1 = observation.openScope()) {
            then(scope1.isNoop()).isTrue();
            then(scope1).isSameAs(Observation.Scope.NOOP);
            then(observationRegistry.getCurrentObservationScope()).isNull();
            then(observationRegistry.getCurrentObservation()).isNull();
            try (Observation.Scope scope2 = observation.openScope()) {
                then(scope2.isNoop()).isTrue();
                then(scope2).isSameAs(Observation.Scope.NOOP);
                then(observationRegistry.getCurrentObservationScope()).isNull();
                then(observationRegistry.getCurrentObservation()).isNull();
            }
            then(observationRegistry.getCurrentObservation()).isNull();
        }

        then(observationRegistry.getCurrentObservation()).isNull();
    }

}
