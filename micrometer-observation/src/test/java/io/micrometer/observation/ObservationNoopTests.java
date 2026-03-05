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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class ObservationNoopTests {

    @ParameterizedTest
    @MethodSource("registries")
    @SuppressWarnings("NullAway")
    void shouldRespectScopesForRegistry(ObservationRegistry registry) {
        then(registry.getCurrentObservation()).isNull();

        Observation observation = Observation.start("foo", registry);
        then(observation.isNoop()).isTrue();
        then(observation).isSameAs(NoopButScopeHandlingObservation.INSTANCE);
        then(registry.getCurrentObservation()).isNull();

        try (Observation.Scope scope1 = observation.openScope()) {
            then(scope1.isNoop()).isFalse();
            then(registry.getCurrentObservationScope()).isSameAs(scope1);
            then(registry.getCurrentObservationScope().getPreviousObservationScope()).isNull();
            then(registry.getCurrentObservation()).isSameAs(observation);
            try (Observation.Scope scope2 = observation.openScope()) {
                then(scope2.isNoop()).isFalse();
                then(registry.getCurrentObservationScope()).isSameAs(scope2);
                then(registry.getCurrentObservationScope().getPreviousObservationScope()).isSameAs(scope1);
                then(registry.getCurrentObservation()).isSameAs(observation);
            }
            then(registry.getCurrentObservation()).isSameAs(observation);
        }

        then(registry.getCurrentObservation()).isNull();
    }

    static Stream<Arguments> registries() {
        ObservationRegistry disabledByPredicate = ObservationRegistry.create();
        disabledByPredicate.observationConfig().observationHandler(new ObservationTextPublisher());
        disabledByPredicate.observationConfig().observationPredicate((name, context) -> false);

        ObservationRegistry noHandlers = ObservationRegistry.create();

        return Stream.of(argumentSet("disabledByPredicate", disabledByPredicate),
                argumentSet("no-op registry", ObservationRegistry.NOOP), argumentSet("no handlers", noHandlers));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRespectScopesIfNullRegistryIsUsed() {
        Observation observation = Observation.start("foo", null);
        then(observation.isNoop()).isTrue();
        then(observation).isSameAs(NoopButScopeHandlingObservation.INSTANCE);

        try (Observation.Scope scope1 = observation.openScope()) {
            then(scope1.isNoop()).isFalse();

            try (Observation.Scope scope2 = observation.openScope()) {
                then(scope2.isNoop()).isFalse();
            }
        }
    }

}
