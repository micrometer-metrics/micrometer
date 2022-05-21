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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Assertion methods for {@code MeterRegistry}s.
 * <p>
 * To create a new instance of this class, invoke
 * {@link TestObservationRegistryAssert#assertThat(TestObservationRegistry)} or
 * {@link TestObservationRegistryAssert#then(TestObservationRegistry)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public class TestObservationRegistryAssert
        extends ObservationRegistryAssert<TestObservationRegistryAssert, TestObservationRegistry> {

    protected TestObservationRegistryAssert(TestObservationRegistry actual) {
        super(actual, TestObservationRegistryAssert.class);
    }

    /**
     * Creates the assert object for {@link TestObservationRegistry}.
     * @param actual mock observation registry to assert against
     * @return meter registry assertions
     */
    public static TestObservationRegistryAssert assertThat(TestObservationRegistry actual) {
        return new TestObservationRegistryAssert(actual);
    }

    /**
     * Creates the assert object for {@link TestObservationRegistry}.
     * @param actual mock observation registry to assert against
     * @return meter registry assertions
     */
    public static TestObservationRegistryAssert then(TestObservationRegistry actual) {
        return new TestObservationRegistryAssert(actual);
    }

    /**
     * Verifies that there's only one {@link Observation} and continues assertions for it.
     * @return this
     * @throws AssertionError if there is none or more than one observation
     */
    public TestObservationRegistryAssertReturningObservationContextAssert hasSingleObservationThat() {
        List<TestObservationRegistry.TestObservationContext> contexts = actual.getContexts();
        if (contexts.isEmpty()) {
            failForNoObservations();
        }
        else if (contexts.size() != 1) {
            failWithMessage(
                    "There must be only a single observation, however there are <%s> registered observations with names <%s>",
                    contexts.size(), observationNames(contexts));
        }
        return new TestObservationRegistryAssertReturningObservationContextAssert(contexts.get(0), this);
    }

    private void failForNoObservations() {
        failWithMessage(
                "There are no observations registered. You have forgotten to start your observation. Remember to call <observation.start()> method");
    }

    /**
     * Verifies that there's only one {@link Observation} with a given name and continues
     * assertions for it.
     * @return this
     * @throws AssertionError if there is no matching observation
     */
    public That hasObservationWithNameEqualTo(String name) {
        List<TestObservationRegistry.TestObservationContext> contexts = this.actual.getContexts();
        if (contexts.isEmpty()) {
            failForNoObservations();
        }
        TestObservationRegistry.TestObservationContext testObservationContext = contexts.stream()
                .filter(mock -> Objects.equals(name, mock.getContext().getName())).findFirst().orElseGet(() -> {
                    failWithMessage("There are no observations with name equal to <%s>. Available names are <%s>", name,
                            observationNames(contexts));
                    return null;
                });
        return new That(testObservationContext, this);
    }

    private String observationNames(List<TestObservationRegistry.TestObservationContext> contexts) {
        return contexts.stream().map(m -> m.getContext().getName()).collect(Collectors.joining(","));
    }

    /**
     * Verifies that there's only one {@link Observation} with a given name (ignoring
     * case) and continues assertions for it.
     * @return this
     * @throws AssertionError if there is no matching observation
     */
    public That hasObservationWithNameEqualToIgnoringCase(String name) {
        List<TestObservationRegistry.TestObservationContext> contexts = this.actual.getContexts();
        if (contexts.isEmpty()) {
            failForNoObservations();
        }
        TestObservationRegistry.TestObservationContext testObservationContext = contexts.stream()
                .filter(mock -> name != null && name.equalsIgnoreCase(mock.getContext().getName())).findFirst()
                .orElseGet(() -> {
                    failWithMessage(
                            "There are no observations with name equal to ignoring case <%s>. Available names are <%s>",
                            name, observationNames(contexts));
                    return null;
                });
        return new That(testObservationContext, this);
    }

    /**
     * Provides assertions for {@link Observation} and allows coming back to
     * {@link TestObservationRegistryAssert}.
     */
    public static final class That {

        private final TestObservationRegistryAssert originalAssert;

        private final TestObservationRegistry.TestObservationContext testContext;

        private That(TestObservationRegistry.TestObservationContext testContext,
                TestObservationRegistryAssert observationContextAssert) {
            this.testContext = testContext;
            this.originalAssert = observationContextAssert;
        }

        /**
         * Synactic sugar to smoothly go to
         * {@link TestObservationRegistryAssertReturningObservationContextAssert}.
         * @return {@link TestObservationRegistryAssertReturningObservationContextAssert}
         * assert object
         */
        public TestObservationRegistryAssertReturningObservationContextAssert that() {
            return new TestObservationRegistryAssertReturningObservationContextAssert(this.testContext,
                    this.originalAssert);
        }

    }

    /**
     * Provides assertions for {@link Observation} and allows coming back to
     * {@link TestObservationRegistryAssert}.
     */
    public static final class TestObservationRegistryAssertReturningObservationContextAssert
            extends ObservationContextAssert<TestObservationRegistryAssertReturningObservationContextAssert> {

        private final TestObservationRegistryAssert originalAssert;

        private final TestObservationRegistry.TestObservationContext testContext;

        private TestObservationRegistryAssertReturningObservationContextAssert(
                TestObservationRegistry.TestObservationContext testContext,
                TestObservationRegistryAssert observationContextAssert) {
            super(testContext.getContext());
            this.testContext = testContext;
            this.originalAssert = observationContextAssert;
        }

        /**
         * Verifies that the {@link Observation} is started.
         * @return this
         * @throws AssertionError if the {@link Observation} is not started
         */
        public TestObservationRegistryAssertReturningObservationContextAssert hasBeenStarted() {
            isNotNull();
            if (!this.testContext.isObservationStarted()) {
                failWithMessage("Observation is not started");
            }
            return this;
        }

        /**
         * Verifies that the {@link Observation} is stopped.
         * @return this
         * @throws AssertionError if the {@link Observation} is not stopped
         */
        public TestObservationRegistryAssertReturningObservationContextAssert hasBeenStopped() {
            isNotNull();
            if (!this.testContext.isObservationStopped()) {
                failWithMessage("Observation is not stopped");
            }
            return this;
        }

        /**
         * Verifies that the {@link Observation} is not started.
         * @return this
         * @throws AssertionError if the {@link Observation} is started
         */
        public TestObservationRegistryAssertReturningObservationContextAssert isNotStarted() {
            isNotNull();
            if (this.testContext.isObservationStarted()) {
                failWithMessage("Observation is started");
            }
            return this;
        }

        /**
         * Verifies that the {@link Observation} is not stopped.
         * @return this
         * @throws AssertionError if the {@link Observation} is stopped
         */
        public TestObservationRegistryAssertReturningObservationContextAssert isNotStopped() {
            isNotNull();
            if (this.testContext.isObservationStopped()) {
                failWithMessage("Observation is stopped");
            }
            return this;
        }

        /**
         * Returns the original {@link TestObservationRegistryAssert} for chaining.
         * @return the original assertion for chaining
         */
        public TestObservationRegistryAssert backToTestObservationRegistry() {
            return this.originalAssert;
        }

    }

}
