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
package io.micrometer.observation.tck;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("rawtypes")
class ObservationRegistryAssertTests {

    ObservationRegistry registry;

    ObservationRegistryAssert registryAssert;

    @BeforeEach
    void beforeEach() {
        this.registry = ObservationRegistry.create();
        this.registryAssert = ObservationRegistryAssert.assertThat(registry);

        registry.observationConfig().observationHandler(c -> true); // prevent Noop
                                                                    // instances
    }

    @Test
    void assertionErrorThrownWhenRemainingObservationFound() {
        Observation observation = Observation.start("hello", registry);

        try (Observation.Scope scope = observation.openScope()) {
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
        assertThatThrownBy(() -> registryAssert.hasRemainingCurrentObservation()).isInstanceOf(AssertionError.class)
            .hasMessageContaining("Expected an observation in the registry but found none");
    }

    @Test
    void noAssertionErrorThrownWhenCurrentObservationPresent() {
        Observation observation = Observation.start("hello", registry);

        try (Observation.Scope scope = observation.openScope()) {
            assertThatCode(() -> this.registryAssert.hasRemainingCurrentObservation()).doesNotThrowAnyException();
        }
    }

    @Test
    void assertionErrorThrownWhenRemainingObservationNotSameAs() {
        Observation observation = Observation.createNotStarted("foo", this.registry);

        assertThatThrownBy(() -> this.registryAssert.hasRemainingCurrentObservationSameAs(observation))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("but there was no current observation");

        Observation anotherObservation = Observation.start("bar", this.registry);
        try (Observation.Scope scope = anotherObservation.openScope()) {
            assertThatThrownBy(() -> this.registryAssert.hasRemainingCurrentObservationSameAs(observation))
                .hasMessageContaining("Expected current observation in the registry to be same as <{name=foo")
                .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                    then(error.getActual().getStringRepresentation()).contains("bar");
                    then(error.getExpected().getStringRepresentation()).contains("foo");
                });
        }
    }

    @Test
    void noAssertionErrorThrownWhenCurrentObservationSameAs() {
        Observation observation = Observation.start("hello", registry);

        try (Observation.Scope scope = observation.openScope()) {
            assertThatCode(() -> this.registryAssert.hasRemainingCurrentObservationSameAs(observation))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void assertionErrorThrownWhenRemainingObservationSameAs() {
        Observation observation = Observation.createNotStarted("foo", this.registry);

        try (Observation.Scope scope = observation.openScope()) {
            assertThatThrownBy(() -> this.registryAssert.doesNotHaveRemainingCurrentObservationSameAs(observation))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected current observation in the registry to be different than");
        }
    }

    @Test
    void noAssertionErrorThrownWhenCurrentObservationNotSameAs() {
        Observation noop = Observation.start("noop", registry);

        assertThatCode(() -> this.registryAssert.doesNotHaveRemainingCurrentObservationSameAs(noop))
            .doesNotThrowAnyException();
    }

    @Test
    void failsDoesNotHaveAnyRemainingCurrentScope() {
        Observation o = Observation.start("active", registry);
        try (Observation.Scope scope = o.openScope()) {
            assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> {
                this.registryAssert.doesNotHaveAnyRemainingCurrentScope();
            })
                .withMessage(
                        "Expected no current Scope in the registry but found one tied to observation named <active>");
        }
    }

    @Test
    void passesDoesNotHaveAnyRemainingCurrentScope() {
        Observation o = Observation.start("active", registry);

        assertThatNoException().isThrownBy(() -> this.registryAssert.doesNotHaveAnyRemainingCurrentScope());
    }

    @Test
    void failsHasRemainingCurrentScope() {
        assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> this.registryAssert.hasRemainingCurrentScope())
            .withMessage("Expected a current Scope in the registry but found none");
    }

    @Test
    void passesHasRemainingCurrentScope() {
        Observation o = Observation.start("active", registry);
        try (Observation.Scope scope = o.openScope()) {
            assertThatNoException().isThrownBy(() -> this.registryAssert.hasRemainingCurrentScope());
        }
    }

    @Test
    void failsHasRemainingCurrentScopeSameAs() {
        Observation o1 = Observation.start("old", registry);
        Observation o2 = Observation.start("new", registry);
        Observation.Scope scope1 = o1.openScope();
        scope1.close();
        try (Observation.Scope scope2 = o2.openScope()) {
            assertThatExceptionOfType(AssertionFailedError.class)
                .isThrownBy(() -> this.registryAssert.hasRemainingCurrentScopeSameAs(scope1))
                .withMessage(
                        "Expected current Scope in the registry to be same as a provided Scope tied to observation named <old>"
                                + " but was a different one (tied to observation named <new>)")
                .satisfies(error -> {
                    then(error.getActual().getStringRepresentation()).isEqualTo("new");
                    then(error.getExpected().getStringRepresentation()).isEqualTo("old");
                });
            ;
        }
    }

    @Test
    void failsHasRemainingCurrentScopeSameAsWhenNoCurrentScope() {
        Observation old = Observation.start("old", registry);
        Observation.Scope closed = old.openScope();
        closed.close();

        assertThatExceptionOfType(AssertionError.class)
            .isThrownBy(() -> this.registryAssert.hasRemainingCurrentScopeSameAs(closed))
            .withMessage(
                    "Expected current Scope in the registry to be same as a provided Scope tied to observation named <old>"
                            + " but there was no current scope");
    }

    @Test
    void passesHasRemainingCurrentScopeSameAs() {
        Observation o = Observation.start("active", registry);
        try (Observation.Scope scope = o.openScope()) {
            assertThatNoException().isThrownBy(() -> this.registryAssert.hasRemainingCurrentScopeSameAs(scope));
        }
    }

    @Test
    void failsDoesNotHaveRemainingCurrentScopeSameAs() {
        Observation o1 = Observation.start("old", registry);
        Observation o2 = Observation.start("new", registry);
        Observation.Scope scope1 = o1.openScope();
        scope1.close();
        try (Observation.Scope scope2 = o2.openScope()) {
            assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> this.registryAssert.doesNotHaveRemainingCurrentScopeSameAs(scope2))
                .withMessage("Expected current Scope in the registry to be different from a provided Scope "
                        + "tied to observation named <new> but was the same");
        }
    }

    @Test
    void passesDoesNotHaveRemainingCurrentScopeSameAs() {
        Observation o1 = Observation.start("old", registry);
        Observation o2 = Observation.start("new", registry);
        Observation.Scope scope1 = o1.openScope();
        scope1.close();
        try (Observation.Scope scope2 = o2.openScope()) {
            assertThatNoException()
                .isThrownBy(() -> this.registryAssert.doesNotHaveRemainingCurrentScopeSameAs(scope1));
        }
    }

}
