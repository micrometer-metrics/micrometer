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
import org.junit.jupiter.api.Test;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class TestObservationRegistryAssertTests {

    TestObservationRegistry registry = TestObservationRegistry.create();

    @Test
    void should_fail_when_observation_not_started() {
        Observation.createNotStarted("foo", registry).stop();

        thenThrownBy(
                () -> TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStarted())
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("You have forgotten to start your observation");
    }

    @Test
    void should_not_fail_when_observation_started() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenNoException().isThrownBy(
                () -> TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStarted());
    }

    @Test
    void should_fail_when_observation_not_stopped() {
        Observation.createNotStarted("foo", registry).start();

        thenThrownBy(
                () -> TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStopped())
                        .isInstanceOf(AssertionError.class).hasMessageContaining("Observation is not stopped");
    }

    @Test
    void should_not_fail_when_observation_stopped() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenNoException().isThrownBy(
                () -> TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStopped());
    }

    @Test
    void should_fail_when_observation_stopped() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().isNotStopped())
                .isInstanceOf(AssertionError.class).hasMessageContaining("Observation is stopped");
    }

    @Test
    void should_not_fail_when_observation_not_stopped() {
        Observation.createNotStarted("foo", registry).start();

        thenNoException().isThrownBy(
                () -> TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().isNotStopped());
    }

    @Test
    void should_fail_when_no_observation_with_name_found() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry).hasObservationWithNameEqualTo("bar"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Available names are <foo>");
    }

    @Test
    void should_not_fail_when_observation_with_name_found() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("foo").that().hasBeenStarted());
    }

    @Test
    void should_fail_when_no_observation_with_name_ignoring_case_found() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualToIgnoringCase("bar")).isInstanceOf(AssertionError.class)
                        .hasMessageContaining("Available names are <foo>");
    }

    @Test
    void should_not_fail_when_observation_with_name_ignoring_case_found() {
        Observation.createNotStarted("FOO", registry).start().stop();

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualToIgnoringCase("foo").that().hasBeenStarted());
    }

    @Test
    void should_jump_to_and_back_from_context_assert() {
        new Example(registry).run();

        thenNoException().isThrownBy(() -> assertThat(registry).hasObservationWithNameEqualTo("foo").that()
                .hasHighCardinalityKeyValue("highTag", "highTagValue")
                .hasLowCardinalityKeyValue("lowTag", "lowTagValue").hasBeenStarted().hasBeenStopped()
                .backToTestObservationRegistry().doesNotHaveAnyRemainingCurrentObservation());
    }

    static class Example {

        private final ObservationRegistry registry;

        Example(ObservationRegistry registry) {
            this.registry = registry;
        }

        void run() {
            Observation.createNotStarted("foo", registry).lowCardinalityKeyValue("lowTag", "lowTagValue")
                    .highCardinalityKeyValue("highTag", "highTagValue").observe(() -> System.out.println("Hello"));
        }

    }

}
