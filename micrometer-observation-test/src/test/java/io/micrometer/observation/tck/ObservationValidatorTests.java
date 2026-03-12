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
package io.micrometer.observation.tck;

import io.micrometer.observation.NullObservation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.Observation.Scope;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ObservationValidator}.
 *
 * @author Jonatan Ivanov
 */
class ObservationValidatorTests {

    private final ObservationRegistry registry = TestObservationRegistry.create();

    @Test
    void doubleStartShouldBeInvalid() {
        assertThatThrownBy(() -> Observation.start("test", registry).start())
            .isExactlyInstanceOf(InvalidObservationException.class)
            .hasNoCause()
            .hasMessage("Invalid start: Observation 'test' has already been started")
            .satisfies(exception -> assertThat(exception.toString()).matches(
                    "(?s)^io\\.micrometer\\.observation\\.tck\\.InvalidObservationException: Invalid start: Observation 'test' has already been started\n"
                            + "START: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$doubleStartShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)\n"
                            + "START: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$doubleStartShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)$"));
    }

    @Test
    void stopBeforeStartShouldBeInvalid() {
        assertThatThrownBy(() -> Observation.createNotStarted("test", registry).stop())
            .isExactlyInstanceOf(InvalidObservationException.class)
            .hasNoCause()
            .hasMessage("Invalid stop: Observation 'test' has not been started yet")
            .satisfies(exception -> assertThat(exception.toString()).matches(
                    "(?s)^io\\.micrometer\\.observation\\.tck\\.InvalidObservationException: Invalid stop: Observation 'test' has not been started yet\n"
                            + "STOP: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$stopBeforeStartShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)$"));
    }

    @Test
    void errorBeforeStartShouldBeInvalid() {
        assertThatThrownBy(() -> Observation.createNotStarted("test", registry).error(new RuntimeException()))
            .isExactlyInstanceOf(InvalidObservationException.class)
            .hasNoCause()
            .hasMessage("Invalid error signal: Observation 'test' has not been started yet")
            .satisfies(exception -> assertThat(exception.toString()).matches(
                    "(?s)^io\\.micrometer\\.observation\\.tck\\.InvalidObservationException: Invalid error signal: Observation 'test' has not been started yet\n"
                            + "ERROR: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$errorBeforeStartShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)$"));
    }

    @Test
    void eventBeforeStartShouldBeInvalid() {
        assertThatThrownBy(() -> Observation.createNotStarted("test", registry).event(Event.of("test")))
            .isExactlyInstanceOf(InvalidObservationException.class)
            .hasNoCause()
            .hasMessage("Invalid event signal: Observation 'test' has not been started yet")
            .satisfies(exception -> assertThat(exception.toString()).matches(
                    "(?s)^io\\.micrometer\\.observation\\.tck\\.InvalidObservationException: Invalid event signal: Observation 'test' has not been started yet\n"
                            + "EVENT: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$eventBeforeStartShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)$"));
    }

    @Test
    @SuppressWarnings("resource")
    void scopeBeforeStartShouldBeInvalid() {
        // Since openScope throws an exception, reset and close can't happen
        assertThatThrownBy(() -> Observation.createNotStarted("test", registry).openScope())
            .isExactlyInstanceOf(InvalidObservationException.class)
            .hasNoCause()
            .hasMessage("Invalid scope opening: Observation 'test' has not been started yet")
            .satisfies(exception -> assertThat(exception.toString()).matches(
                    "(?s)^io\\.micrometer\\.observation\\.tck\\.InvalidObservationException: Invalid scope opening: Observation 'test' has not been started yet\n"
                            + "SCOPE_OPEN: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$scopeBeforeStartShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)$"));
    }

    @Test
    void observeAfterStartShouldBeInvalid() {
        assertThatThrownBy(() -> Observation.start("test", registry).observe(() -> ""))
            .isExactlyInstanceOf(InvalidObservationException.class)
            .hasNoCause()
            .hasMessage("Invalid start: Observation 'test' has already been started")
            .satisfies(exception -> assertThat(exception.toString()).matches(
                    "(?s)^io\\.micrometer\\.observation\\.tck\\.InvalidObservationException: Invalid start: Observation 'test' has already been started\n"
                            + "START: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$observeAfterStartShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)\n"
                            + "START: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$observeAfterStartShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)$"));
    }

    @Test
    void doubleStopShouldBeInvalid() {
        assertThatThrownBy(() -> {
            Observation observation = Observation.start("test", registry);
            observation.stop();
            observation.stop();
        }).isExactlyInstanceOf(InvalidObservationException.class)
            .hasNoCause()
            .hasMessage("Invalid stop: Observation 'test' has already been stopped")
            .satisfies(exception -> assertThat(exception.toString()).matches(
                    "(?s)^io\\.micrometer\\.observation\\.tck\\.InvalidObservationException: Invalid stop: Observation 'test' has already been stopped\n"
                            + "START: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$doubleStopShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)\n"
                            + "STOP: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$doubleStopShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)\n"
                            + "STOP: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$doubleStopShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)$"));
    }

    @Test
    void errorAfterStopShouldBeInvalid() {
        assertThatThrownBy(() -> {
            Observation observation = Observation.start("test", registry);
            observation.stop();
            observation.error(new RuntimeException());
        }).isExactlyInstanceOf(InvalidObservationException.class)
            .hasNoCause()
            .hasMessage("Invalid error signal: Observation 'test' has already been stopped")
            .satisfies(exception -> assertThat(exception.toString()).matches(
                    "(?s)^io\\.micrometer\\.observation\\.tck\\.InvalidObservationException: Invalid error signal: Observation 'test' has already been stopped\n"
                            + "START: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$errorAfterStopShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)\n"
                            + "STOP: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$errorAfterStopShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)\n"
                            + "ERROR: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$errorAfterStopShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)$"));
    }

    @Test
    void eventAfterStopShouldBeInvalid() {
        assertThatThrownBy(() -> {
            Observation observation = Observation.start("test", registry);
            observation.stop();
            observation.event(Event.of("test"));
        }).isExactlyInstanceOf(InvalidObservationException.class)
            .hasNoCause()
            .hasMessage("Invalid event signal: Observation 'test' has already been stopped")
            .satisfies(exception -> assertThat(exception.toString()).matches(
                    "(?s)^io\\.micrometer\\.observation\\.tck\\.InvalidObservationException: Invalid event signal: Observation 'test' has already been stopped\n"
                            + "START: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$eventAfterStopShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)\n"
                            + "STOP: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$eventAfterStopShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)\n"
                            + "EVENT: io\\.micrometer\\.observation\\.tck\\.ObservationValidatorTests\\.lambda\\$eventAfterStopShouldBeInvalid\\$\\d+\\(ObservationValidatorTests\\.java:\\d+\\)$"));
    }

    @Test
    void scopeOpenAfterStopShouldBeValid() {
        Observation observation = Observation.start("test", registry);
        observation.stop();
        Scope scope = observation.openScope();

        // The scope should be closed to clear its entry in the static
        // localObservationScope in the SimpleObservationRegistry. Otherwise, it will
        // pollute it and could affect other tests.
        scope.close();
    }

    @Test
    @SuppressWarnings("resource")
    void scopeResetAfterStopShouldBeValid() {
        Observation observation = Observation.start("test", registry);
        Scope scope = observation.openScope();
        observation.stop();
        scope.reset();
    }

    @Test
    void scopeCloseAfterStopShouldBeValid() {
        Observation observation = Observation.start("test", registry);
        Scope scope = observation.openScope();
        observation.stop();
        scope.close();
    }

    @Test
    void startEventStopShouldBeValid() {
        Observation.start("test", registry).event(Event.of("test")).stop();
    }

    @Test
    void startEventErrorStopShouldBeValid() {
        Observation.start("test", registry).event(Event.of("test")).error(new RuntimeException()).stop();
    }

    @Test
    void startErrorEventStopShouldBeValid() {
        Observation.start("test", registry).error(new RuntimeException()).event(Event.of("test")).stop();
    }

    @Test
    void startScopeEventStopShouldBeValid() {
        Observation observation = Observation.start("test", registry);
        observation.openScope().close();
        observation.event(Event.of("test"));
        observation.stop();
    }

    @Test
    void startScopeEventErrorStopShouldBeValid() {
        Observation observation = Observation.start("test", registry);
        Scope scope = observation.openScope();
        observation.event(Event.of("test"));
        observation.error(new RuntimeException());
        scope.close();
        observation.stop();
    }

    @Test
    void startScopeErrorEventStopShouldBeValid() {
        Observation observation = Observation.start("test", registry);
        Scope scope = observation.openScope();
        observation.error(new RuntimeException());
        observation.event(Event.of("test"));
        scope.close();
        observation.stop();
    }

    @Test
    void startErrorErrorStopShouldBeValid() {
        Observation.start("test", registry).error(new RuntimeException()).error(new RuntimeException()).stop();
    }

    @Test
    void nullObservationShouldBeIgnored() {
        new NullObservation(registry).openScope();
    }

    @Test
    void capabilitiesCanBeDisabledUsingTheBuilder() {
        TestObservationRegistry registry = TestObservationRegistry.builder()
            .validateObservationsWithTheSameNameHavingTheSameSetOfLowCardinalityKeys(false)
            .build();
        Observation.createNotStarted("test", registry).lowCardinalityKeyValue("key1", "value1").start().stop();
        Observation.createNotStarted("test", registry).start().stop();
        Observation.createNotStarted("test", registry).lowCardinalityKeyValue("key2", "value2").start().stop();
    }

    @Test
    void observationsWithTheSameNameShouldHaveTheSameSetOfLowCardinalityKeysNotValidatedByDefaultUsingCreate() {
        TestObservationRegistry registry = TestObservationRegistry.create();

        Observation.createNotStarted("test", registry).lowCardinalityKeyValue("key1", "value1").start().stop();
        Observation.createNotStarted("test", registry).start().stop();
        Observation.createNotStarted("test", registry).lowCardinalityKeyValue("key2", "value2").start().stop();
    }

    private void verifyThatValidateObservationsWithTheSameNameHavingTheSameSetOfLowCardinalityKeysWorks(
            TestObservationRegistry registry) {
        Observation.createNotStarted("test", registry).lowCardinalityKeyValue("key1", "value1").start().stop();
        assertThatThrownBy(() -> {
            Observation.createNotStarted("test", registry).start().stop();
        }).isExactlyInstanceOf(InvalidObservationException.class)
            .hasMessageContaining(
                    "Using a consistent set of low cardinality keys for Observations with the same name is recommended best practice if metrics will be produced from the Observations.");

        assertThatThrownBy(() -> {
            Observation.createNotStarted("test", registry).lowCardinalityKeyValue("key2", "value2").start().stop();
        }).isExactlyInstanceOf(InvalidObservationException.class)
            .hasMessageContaining(
                    "Using a consistent set of low cardinality keys for Observations with the same name is recommended best practice if metrics will be produced from the Observations.");

        Observation.createNotStarted("test", registry).lowCardinalityKeyValue("key1", "value2").start().stop();
    }

    @Test
    void observationsWithTheSameNameShouldHaveTheSameSetOfLowCardinalityKeysIfEnabledUsingTheBuilder() {
        TestObservationRegistry registry = TestObservationRegistry.builder()
            .validateObservationsWithTheSameNameHavingTheSameSetOfLowCardinalityKeys(true)
            .build();
        verifyThatValidateObservationsWithTheSameNameHavingTheSameSetOfLowCardinalityKeysWorks(registry);
    }

    @Test
    void observationsWithTheSameNameShouldHaveTheSameSetOfLowCardinalityKeysWhenEnabledUsingTheBuilder() {
        TestObservationRegistry registry = TestObservationRegistry.builder()
            .validateObservationsWithTheSameNameHavingTheSameSetOfLowCardinalityKeys(true)
            .build();
        verifyThatValidateObservationsWithTheSameNameHavingTheSameSetOfLowCardinalityKeysWorks(registry);
    }

    @Test
    void scopeClosedInCorrectOrderShouldBeValid() {
        TestObservationRegistry registry = TestObservationRegistry.builder()
            .validateScopesClosedInReverseOrderOfOpening(true)
            .build();
        Observation observation = Observation.start("test", registry);
        Scope scope1 = observation.openScope();
        Scope scope2 = observation.openScope();
        scope2.close();
        scope1.close();
        observation.stop();
    }

    @Test
    void scopeClosedInWrongOrderShouldBeInvalid() {
        TestObservationRegistry registry = TestObservationRegistry.builder()
            .validateScopesClosedInReverseOrderOfOpening(true)
            .build();
        Observation obs1 = Observation.start("obs1", registry);
        Scope scope1 = obs1.openScope();
        Observation obs2 = Observation.start("obs2", registry);
        obs2.openScope();
        assertThatThrownBy(scope1::close).isExactlyInstanceOf(InvalidObservationException.class)
            .hasMessageContaining("Invalid scope closing order")
            .hasMessageContaining("obs1")
            .hasMessageContaining("obs2");
    }

    @Test
    void scopeClosedOnSameThreadShouldBeValid() {
        TestObservationRegistry registry = TestObservationRegistry.builder()
            .validateScopesOpenedAndClosedOnTheSameThread(true)
            .build();
        Observation observation = Observation.start("test", registry);
        Scope scope = observation.openScope();
        scope.close();
        observation.stop();
    }

    @Test
    void multipleScopesOpenedAndClosedOnSameThreadShouldBeValid() {
        TestObservationRegistry registry = TestObservationRegistry.builder()
            .validateScopesOpenedAndClosedOnTheSameThread(true)
            .build();
        Observation observation = Observation.start("test", registry);
        Scope scope1 = observation.openScope();
        Scope scope2 = observation.openScope();
        Scope scope3 = observation.openScope();
        scope3.close();
        scope2.close();
        scope1.close();
        observation.stop();
    }

    @Test
    void multipleScopesWithOneClosedOnDifferentThreadShouldBeInvalid() throws Exception {
        TestObservationRegistry registry = TestObservationRegistry.builder()
            .validateScopesOpenedAndClosedOnTheSameThread(true)
            .build();
        Observation observation = Observation.start("test", registry);
        Scope scope1 = observation.openScope();
        Scope scope2 = observation.openScope();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                scope2.close();
            }
            catch (Throwable t) {
                error.set(t);
            }
            finally {
                latch.countDown();
            }
        });
        thread.start();
        latch.await();

        assertThat(error.get()).isExactlyInstanceOf(InvalidObservationException.class)
            .hasMessageContaining("Invalid scope closing thread")
            .hasMessageContaining("test");

        // scope1 should still close fine on the original thread
        scope1.close();
        observation.stop();
    }

    @Test
    void scopeClosedOnDifferentThreadShouldBeInvalid() throws Exception {
        TestObservationRegistry registry = TestObservationRegistry.builder()
            .validateScopesOpenedAndClosedOnTheSameThread(true)
            .build();
        Observation observation = Observation.start("test", registry);
        Scope scope = observation.openScope();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                scope.close();
            }
            catch (Throwable t) {
                error.set(t);
            }
            finally {
                latch.countDown();
            }
        });
        thread.start();
        latch.await();

        assertThat(error.get()).isExactlyInstanceOf(InvalidObservationException.class)
            .hasMessageContaining("Invalid scope closing thread")
            .hasMessageContaining("test");
    }

    @Test
    void scopeValidationNotEnabledByDefault() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        Observation obs1 = Observation.start("obs1", registry);
        Scope scope1 = obs1.openScope();
        Observation obs2 = Observation.start("obs2", registry);
        Scope scope2 = obs2.openScope();
        // Closing in wrong order should not throw when validation is not enabled
        scope1.close();
        scope2.close();
        obs2.stop();
        obs1.stop();
    }

    @Test
    void nestedScopesOnSameObservationClosedInCorrectOrderShouldBeValid() {
        TestObservationRegistry registry = TestObservationRegistry.builder()
            .validateScopesClosedInReverseOrderOfOpening(true)
            .validateScopesOpenedAndClosedOnTheSameThread(true)
            .build();
        Observation observation = Observation.start("test", registry);
        Scope scope1 = observation.openScope();
        Scope scope2 = observation.openScope();
        Scope scope3 = observation.openScope();
        scope3.close();
        scope2.close();
        scope1.close();
        observation.stop();
    }

}
