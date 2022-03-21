/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.tck;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("rawtypes")
class ObservationRegistryAssertTests {

    ObservationRegistry registry = ObservationRegistry.create();

    ObservationRegistryAssert registryAssert = ObservationRegistryAssert.assertThat(registry);

    @Test
    void assertionErrorThrownWhenRemainingObservationFound() {
        Observation observation = Observation.start("hello", registry);

        try (Observation.Scope ws = observation.openScope()) {
            assertThatThrownBy(() -> registryAssert.doesNotHaveAnyRemainingCurrentObservation())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected no current observation in the registry but found one");
        }
    }

    @Test
    void noAssertionErrorThrownWhenNoCurrentObservation() {
        assertThatCode(() -> this.registryAssert.doesNotHaveAnyRemainingCurrentObservation())
                .doesNotThrowAnyException();
    }

    @Test
    void assertionErrorThrownWhenRemainingObservationNotFound() {
        assertThatThrownBy(() -> registryAssert.hasRemainingCurrentObservation())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected an observation in the registry but found none");
    }

    @Test
    void noAssertionErrorThrownWhenCurrentObservationPresent() {
        Observation observation = Observation.start("hello", registry);

        try (Observation.Scope ws = observation.openScope()) {
            assertThatCode(() -> this.registryAssert.hasRemainingCurrentObservation())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void assertionErrorThrownWhenRemainingObservationNotSameAs() {
        Observation observation = Observation.createNotStarted("foo", this.registry);

        assertThatThrownBy(() -> this.registryAssert.hasRemainingCurrentObservationSameAs(observation))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected current observation in the registry to be same as <{name=foo");
    }

    @Test
    void noAssertionErrorThrownWhenCurrentObservationSameAs() {
        Observation observation = Observation.start("hello", registry);

        try (Observation.Scope ws = observation.openScope()) {
            assertThatCode(() -> this.registryAssert.hasRemainingCurrentObservationSameAs(observation))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void assertionErrorThrownWhenRemainingObservationSameAs() {
        Observation observation = Observation.createNotStarted("foo", this.registry);

        try (Observation.Scope ws = observation.openScope()) {
            assertThatThrownBy(() -> this.registryAssert.doesNotHaveRemainingCurrentObservationSameAs(observation))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected current observation in the registry to be different than");
        }
    }

    @Test
    void noAssertionErrorThrownWhenCurrentObservationNotSameAs() {
        Observation observation = Observation.start("hello", registry);

        assertThatCode(() -> this.registryAssert.doesNotHaveRemainingCurrentObservationSameAs(observation))
                     .doesNotThrowAnyException();
    }

}
