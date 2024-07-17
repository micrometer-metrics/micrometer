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

import io.micrometer.common.KeyValue;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.BDDAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class TestObservationRegistryAssertTests {

    TestObservationRegistry registry = TestObservationRegistry.create();

    @Test
    void should_clear_context_entries() {
        Observation.createNotStarted("FOO", registry).start().stop();

        BDDAssertions.then(registry.getContexts()).hasSize(1);

        registry.clear();

        BDDAssertions.then(registry.getContexts()).isEmpty();
    }

    @Test
    void should_not_break_on_multiple_threads() {
        Observation o1 = Observation.createNotStarted("o1", registry);
        Observation o2 = Observation.createNotStarted("o2", registry);
        Observation o3 = Observation.createNotStarted("o3", registry);

        new Thread(() -> o1.start().stop()).start();
        new Thread(() -> o2.start().stop()).start();
        new Thread(() -> o3.start().stop()).start();

        Awaitility.await().pollDelay(Duration.ofMillis(10)).atMost(Duration.ofMillis(100)).untilAsserted(() -> {
            // System.out.println("Registry size [" + registry.getContexts().size() +
            // "]");
            BDDAssertions.then(registry.getContexts()).hasSize(3);
        });
    }

    @Test
    void should_fail_when_observation_not_started() {
        Observation.createNotStarted("foo", registry);

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
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Observation is not stopped");
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
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Observation is stopped");
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
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("Available names are <foo>");
    }

    @Test
    void should_not_fail_when_observation_with_name_found() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasObservationWithNameEqualTo("foo")
            .that()
            .hasBeenStarted());
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
            .hasObservationWithNameEqualToIgnoringCase("foo")
            .that()
            .hasBeenStarted());
    }

    @Test
    void should_fail_when_no_contexts_satisfy_the_assertion() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasHandledContextsThatSatisfy(contexts -> Assertions.assertThat(contexts).hasSize(2)))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_contexts_satisfy_the_assertions() {
        Observation.createNotStarted("FOO", registry).start().stop();

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasHandledContextsThatSatisfy(contexts -> Assertions.assertThat(contexts).hasSize(1)));
    }

    @Test
    void should_fail_when_there_are_observations() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry).doesNotHaveAnyObservation())
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_there_are_no_observations() {
        thenNoException()
            .isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry).doesNotHaveAnyObservation());
    }

    @Test
    void should_fail_when_there_is_no_observation_with_name() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .forAllObservationsWithNameEqualTo("bar", ObservationContextAssert::doesNotHaveError))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_fail_when_all_observations_do_not_match_the_assertion() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .forAllObservationsWithNameEqualTo("foo", ObservationContextAssert::hasError))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_all_observations_match_the_assertion() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .forAllObservationsWithNameEqualTo("foo", ObservationContextAssert::doesNotHaveError));
    }

    @Test
    void should_fail_when_there_is_no_observation_with_name_ignore_case() {
        Observation.createNotStarted("FOO", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .forAllObservationsWithNameEqualToIgnoreCase("bar", ObservationContextAssert::doesNotHaveError))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_fail_when_not_all_observations_match_the_assertion_ignore_case() {
        Observation.createNotStarted("FOO", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .forAllObservationsWithNameEqualToIgnoreCase("foo", ObservationContextAssert::hasError))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_all_observations_match_the_assertion_ignore_case() {
        Observation.createNotStarted("FOO", registry).start().stop();

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .forAllObservationsWithNameEqualToIgnoreCase("foo", ObservationContextAssert::doesNotHaveError));
    }

    @Test
    void should_fail_when_number_of_observations_does_not_match() {
        Observation.createNotStarted("FOO", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry).hasNumberOfObservationsEqualTo(0))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_number_of_observations_matches() {
        Observation.createNotStarted("FOO", registry).start().stop();

        thenNoException()
            .isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry).hasNumberOfObservationsEqualTo(1));
    }

    @Test
    void should_fail_when_names_match_but_number_is_incorrect() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasNumberOfObservationsWithNameEqualTo("foo", 0)).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_fail_when_number_is_correct_but_names_do_not_match() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasNumberOfObservationsWithNameEqualTo("bar", 1)).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_number_and_names_match() {
        Observation.createNotStarted("foo", registry).start().stop();

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasNumberOfObservationsWithNameEqualTo("foo", 1));
    }

    @Test
    void should_fail_when_names_match_but_number_is_incorrect_ignore_case() {
        Observation.createNotStarted("FOO", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasNumberOfObservationsWithNameEqualToIgnoreCase("foo", 0)).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_fail_when_number_is_correct_but_names_do_not_match_ignore_case() {
        Observation.createNotStarted("FOO", registry).start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasNumberOfObservationsWithNameEqualToIgnoreCase("bar", 1)).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_number_and_names_match_ignore_case() {
        Observation.createNotStarted("FOO", registry).start().stop();

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasNumberOfObservationsWithNameEqualToIgnoreCase("foo", 1));
    }

    @Test
    void should_fail_when_key_value_not_matched() {
        Observation.createNotStarted("FOO", registry).lowCardinalityKeyValue("foo", "bar").start().stop();

        thenThrownBy(
                () -> TestObservationRegistryAssert.assertThat(registry).hasAnObservationWithAKeyValue("key", "value"))
            .isInstanceOf(AssertionError.class);

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasAnObservationWithAKeyValue(KeyValue.of("key", "value"))).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_key_value_matched() {
        Observation.createNotStarted("FOO", registry).lowCardinalityKeyValue("foo", "bar").start().stop();

        thenNoException().isThrownBy(
                () -> TestObservationRegistryAssert.assertThat(registry).hasAnObservationWithAKeyValue("foo", "bar"));

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasAnObservationWithAKeyValue(KeyValue.of("foo", "bar")));
    }

    @Test
    void should_fail_when_key_not_matched() {
        Observation.createNotStarted("FOO", registry).lowCardinalityKeyValue("foo", "bar").start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry).hasAnObservationWithAKeyName("key"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_key_matched() {
        Observation.createNotStarted("FOO", registry).lowCardinalityKeyValue("foo", "bar").start().stop();

        thenNoException()
            .isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry).hasAnObservationWithAKeyName("foo"));
    }

    @Test
    void should_fail_when_key_value_not_matched_using_KeyName() {
        Observation.createNotStarted("FOO", registry).lowCardinalityKeyValue("foo", "bar").start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasAnObservationWithAKeyValue(MyKeyName.FOO, "value")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_key_value_matched_using_KeyName() {
        Observation.createNotStarted("FOO", registry).lowCardinalityKeyValue("foo", "bar").start().stop();

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasAnObservationWithAKeyValue(MyKeyName.FOO, "bar"));
    }

    @Test
    void should_fail_when_key_not_matched_using_KeyName() {
        Observation.createNotStarted("FOO", registry).lowCardinalityKeyValue("aaa", "bar").start().stop();

        thenThrownBy(
                () -> TestObservationRegistryAssert.assertThat(registry).hasAnObservationWithAKeyName(MyKeyName.FOO))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_key_matched_using_KeyName() {
        Observation.createNotStarted("FOO", registry).lowCardinalityKeyValue("foo", "bar").start().stop();

        thenNoException().isThrownBy(
                () -> TestObservationRegistryAssert.assertThat(registry).hasAnObservationWithAKeyName(MyKeyName.FOO));
    }

    @Test
    void should_fail_when_no_observation_matches_assertion() {
        Observation.createNotStarted("FOO", registry).lowCardinalityKeyValue("aaa", "bar").start().stop();

        thenThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasAnObservation(observationContextAssert -> observationContextAssert.hasNameEqualTo("FOO")
                .hasLowCardinalityKeyValue("bbb", "bar")))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_fail_when_one_observation_matches_assertion() {
        Observation.createNotStarted("FOO", registry).lowCardinalityKeyValue("foo", "bar").start().stop();

        thenNoException().isThrownBy(() -> TestObservationRegistryAssert.assertThat(registry)
            .hasAnObservation(observationContextAssert -> observationContextAssert.hasNameEqualTo("FOO")
                .hasLowCardinalityKeyValue("foo", "bar")));
    }

    @Test
    void should_jump_to_and_back_from_context_assert() {
        new Example(registry).run();

        thenNoException().isThrownBy(() -> assertThat(registry).hasObservationWithNameEqualTo("foo")
            .that()
            .hasHighCardinalityKeyValue("highTag", "highTagValue")
            .hasLowCardinalityKeyValue("lowTag", "lowTagValue")
            .hasBeenStarted()
            .hasBeenStopped()
            .backToTestObservationRegistry()
            .doesNotHaveAnyRemainingCurrentObservation());
    }

    static class Example {

        private final ObservationRegistry registry;

        Example(ObservationRegistry registry) {
            this.registry = registry;
        }

        void run() {
            Observation.createNotStarted("foo", registry)
                .lowCardinalityKeyValue("lowTag", "lowTagValue")
                .highCardinalityKeyValue("highTag", "highTagValue")
                .observe(() -> System.out.println("Hello"));
        }

    }

    enum MyKeyName implements KeyName {

        FOO {
            @Override
            public String asString() {
                return "foo";
            }
        },

        MAYBE_SOMETHING {

            @Override
            public String asString() {
                return "maybe.something";
            }

            @Override
            public boolean isRequired() {
                return false;
            }
        }

    }

}
