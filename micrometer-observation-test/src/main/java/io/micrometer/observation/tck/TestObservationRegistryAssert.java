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
import org.assertj.core.api.ThrowingConsumer;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
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
        Queue<TestObservationRegistry.TestObservationContext> contexts = actual.getContexts();
        if (contexts.isEmpty()) {
            failForNoObservations();
        }
        else if (contexts.size() != 1) {
            failWithMessage(
                    "There must be only a single observation, however there are <%s> registered observations with names <%s>",
                    contexts.size(), observationNames(contexts));
        }
        return new TestObservationRegistryAssertReturningObservationContextAssert(contexts.peek(), this);
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
        Queue<TestObservationRegistry.TestObservationContext> contexts = this.actual.getContexts();
        if (contexts.isEmpty()) {
            failForNoObservations();
        }
        TestObservationRegistry.TestObservationContext testObservationContext = contexts.stream()
            .filter(mock -> Objects.equals(name, mock.getContext().getName()))
            .findFirst()
            .orElseGet(() -> {
                failWithMessage("There are no observations with name equal to <%s>. Available names are <%s>", name,
                        observationNames(contexts));
                return null;
            });
        return new That(testObservationContext, this);
    }

    private String observationNames(Queue<TestObservationRegistry.TestObservationContext> contexts) {
        return contexts.stream().map(m -> m.getContext().getName()).collect(Collectors.joining(","));
    }

    private String observations() {
        return this.actual.getContexts().stream().map(m -> m.getContext().toString()).collect(Collectors.joining(","));
    }

    /**
     * Verifies that there's at least one {@link Observation} with a given name (ignoring
     * case) and continues assertions for the first found one.
     * @return this
     * @throws AssertionError if there is no matching observation
     */
    public That hasObservationWithNameEqualToIgnoringCase(String name) {
        Queue<TestObservationRegistry.TestObservationContext> contexts = this.actual.getContexts();
        if (contexts.isEmpty()) {
            failForNoObservations();
        }
        TestObservationRegistry.TestObservationContext testObservationContext = contexts.stream()
            .filter(mock -> name != null && name.equalsIgnoreCase(mock.getContext().getName()))
            .findFirst()
            .orElseGet(() -> {
                failWithMessage(
                        "There are no observations with name equal to ignoring case <%s>. Available names are <%s>",
                        name, observationNames(contexts));
                return null;
            });
        return new That(testObservationContext, this);
    }

    /**
     * Verifies that there are no observations registered.
     * @throws AssertionError if there are any registered observations
     */
    public void doesNotHaveAnyObservation() {
        Queue<TestObservationRegistry.TestObservationContext> contexts = this.actual.getContexts();
        if (!contexts.isEmpty()) {
            failWithMessage("There were <%d> observation(s) registered in the registry, expected <0>.",
                    contexts.size());
        }
    }

    /**
     * Verifies that all handled contexts satisfy the provided lambda.
     * @param contextConsumer lambda to assert all handled contexts
     * @return this
     */
    public TestObservationRegistryAssert hasHandledContextsThatSatisfy(
            ThrowingConsumer<List<Observation.Context>> contextConsumer) {
        isNotNull();
        contextConsumer.accept(actual.getContexts()
            .stream()
            .map(TestObservationRegistry.TestObservationContext::getContext)
            .collect(Collectors.toList()));
        return this;
    }

    /**
     * Provides verification for all Observations having the given name.
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).forAllObservationsWithNameEqualTo("foo", observationContextAssert -&gt; observationContextAssert.hasError());
     *
     * // assertions fail - assuming that there was a foo observation but none had errors
     * assertThat(testObservationRegistry).forAllObservationsWithNameEqualTo("foo", observationContextAssert -&gt; observationContextAssert.hasError());</code></pre>
     * @param name searched Observation name
     * @param observationConsumer assertion to be executed for each Observation
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no Observation with the given name
     * @throws AssertionError if there is an Observation with the given name but the
     * additional assertion is not successful
     */
    @SuppressWarnings("rawtypes")
    public TestObservationRegistryAssert forAllObservationsWithNameEqualTo(String name,
            Consumer<ObservationContextAssert> observationConsumer) {
        isNotNull();
        hasObservationWithNameEqualTo(name);
        this.actual.getContexts()
            .stream()
            .filter(f -> name.equals(f.getContext().getName()))
            .forEach(f -> observationConsumer.accept(ObservationContextAssert.then(f.getContext())));
        return this;
    }

    /**
     * Provides verification for all Observations having the given name (ignoring case).
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).forAllObservationsWithNameEqualTo("foo", observationContextAssert -&gt; observationContextAssert.hasError());
     *
     * // assertions fail - assuming that there was a foo observation but none had errors
     * assertThat(testObservationRegistry).forAllObservationsWithNameEqualTo("foo", observationContextAssert -&gt; observationContextAssert.hasError());</code></pre>
     * @param name searched Observation name (ignoring case)
     * @param observationConsumer assertion to be executed for each Observation
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no Observation with the given name (ignoring
     * case)
     * @throws AssertionError if there is an Observation with the given name (ignoring
     * case) but the additional assertion is not successful
     */
    @SuppressWarnings("rawtypes")
    public TestObservationRegistryAssert forAllObservationsWithNameEqualToIgnoreCase(String name,
            Consumer<ObservationContextAssert> observationConsumer) {
        isNotNull();
        hasObservationWithNameEqualToIgnoringCase(name);
        this.actual.getContexts()
            .stream()
            .filter(f -> name.equalsIgnoreCase(f.getContext().getName()))
            .forEach(f -> observationConsumer.accept(ObservationContextAssert.then(f.getContext())));
        return this;
    }

    /**
     * Verifies that there is a proper number of Observations.
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(1);
     *
     * // assertions fail - assuming that there was only 1 observation
     * assertThat(testObservationRegistry).hasNumberOfObservationsEqualTo(2);</code></pre>
     * @param expectedNumberOfObservations expected number of Observations
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if the number of Observations is different from the desired
     * one
     */
    public TestObservationRegistryAssert hasNumberOfObservationsEqualTo(int expectedNumberOfObservations) {
        isNotNull();
        int actualNumberOfObservations = this.actual.getContexts().size();
        if (actualNumberOfObservations != expectedNumberOfObservations) {
            failWithActualExpectedAndMessage(actualNumberOfObservations, expectedNumberOfObservations,
                    "There should be <%s> Observations but there were <%s>. Found following Observations:\n%s",
                    expectedNumberOfObservations, actualNumberOfObservations,
                    observationNames(this.actual.getContexts()));
        }
        return this;
    }

    /**
     * Verifies that there is a proper number of Observations with the given name.
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).hasNumberOfObservationsWithNameEqualTo("foo", 1);
     *
     * // assertions fail - assuming that there is only 1 observation with that name
     * assertThat(testObservationRegistry).hasNumberOfObservationsWithNameEqualTo("foo", 2);</code></pre>
     * @param observationName Observation name
     * @param expectedNumberOfObservations expected number of Observations with the given
     * name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if the number of properly named Observations is different
     * from the desired one
     */
    public TestObservationRegistryAssert hasNumberOfObservationsWithNameEqualTo(String observationName,
            int expectedNumberOfObservations) {
        isNotNull();
        long actualNumberOfObservations = this.actual.getContexts()
            .stream()
            .filter(f -> observationName.equals(f.getContext().getName()))
            .count();
        if (actualNumberOfObservations != expectedNumberOfObservations) {
            failWithActualExpectedAndMessage(actualNumberOfObservations, expectedNumberOfObservations,
                    "There should be <%s> Observations with name <%s> but there were <%s>. Found following Observations:\n%s",
                    expectedNumberOfObservations, observationName, actualNumberOfObservations,
                    observationNames(this.actual.getContexts()));
        }
        return this;
    }

    /**
     * Verifies that there is a proper number of Observations with the given name
     * (ignoring case).
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).hasNumberOfObservationsWithNameEqualToIgnoreCase("foo", 1);
     *
     * // assertions fail - assuming that there's only 1 such observation
     * assertThat(testObservationRegistry).hasNumberOfObservationsWithNameEqualToIgnoreCase("foo", 2);</code></pre>
     * @param observationName Observation name
     * @param expectedNumberOfObservations expected number of Observations with the given
     * name (ignoring case)
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if the number of properly named Observations is different
     * from the desired one
     */
    public TestObservationRegistryAssert hasNumberOfObservationsWithNameEqualToIgnoreCase(String observationName,
            int expectedNumberOfObservations) {
        isNotNull();
        long actualNumberOfObservations = this.actual.getContexts()
            .stream()
            .filter(f -> observationName.equalsIgnoreCase(f.getContext().getName()))
            .count();
        if (actualNumberOfObservations != expectedNumberOfObservations) {
            failWithActualExpectedAndMessage(actualNumberOfObservations, expectedNumberOfObservations,
                    "There should be <%s> Observations with name (ignoring case) <%s> but there were <%s>. Found following Observations:\n%s",
                    expectedNumberOfObservations, observationName, actualNumberOfObservations,
                    observationNames(this.actual.getContexts()));
        }
        return this;
    }

    /**
     * Verifies that there is an Observation with a key value.
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).hasAnObservationWithAKeyValue("foo", "bar");
     *
     * // assertions fail - assuming that there is no such a key value in any observation
     * assertThat(testObservationRegistry).hasAnObservationWithAKeyValue("foo", "bar");</code></pre>
     * @param key expected key name
     * @param value expected key value
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no Observation with given key name and value
     */
    public TestObservationRegistryAssert hasAnObservationWithAKeyValue(String key, String value) {
        isNotNull();
        this.actual.getContexts()
            .stream()
            .flatMap(f -> f.getContext().getAllKeyValues().stream())
            .filter(keyValue -> keyValue.getKey().equals(key) && keyValue.getValue().equals(value))
            .findFirst()
            .orElseThrow(() -> {
                failWithMessage(
                        "There should be at least one Observation with key name <%s> and value <%s> but found none. Found following Observations:\n%s",
                        key, value, observations());
                return new AssertionError();
            });
        return this;
    }

    /**
     * Verifies that there is an Observation with a key value.
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).hasAnObservationWithAKeyValue(KeyValue.of("foo", "bar"));
     *
     * // assertions fail - assuming that there is no such a key value in any observation
     * assertThat(testObservationRegistry).hasAnObservationWithAKeyValue(KeyValue.of("foo", "bar"));</code></pre>
     * @param keyValue expected key value
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no Observation with given key name and value
     * @since 1.12.0
     */
    public TestObservationRegistryAssert hasAnObservationWithAKeyValue(KeyValue keyValue) {
        return hasAnObservationWithAKeyValue(keyValue.getKey(), keyValue.getValue());
    }

    /**
     * Verifies that there is an Observation with a key name.
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).hasAnObservationWithAKeyName("foo");
     *
     * // assertions fail - assuming that there are no observations with such a key name
     * assertThat(testObservationRegistry).hasAnObservationWithAKeyName("foo");</code></pre>
     * @param key expected key name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no Observation with given key name
     */
    public TestObservationRegistryAssert hasAnObservationWithAKeyName(String key) {
        isNotNull();
        this.actual.getContexts()
            .stream()
            .flatMap(f -> f.getContext().getAllKeyValues().stream())
            .filter(keyValue -> keyValue.getKey().equals(key))
            .findFirst()
            .orElseThrow(() -> {
                failWithMessage(
                        "There should be at least one Observation with key name <%s> but found none. Found following Observations:\n%s",
                        key, observations());
                return new AssertionError();
            });
        return this;
    }

    /**
     * Verifies that there is an Observation with a key value.
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).hasAnObservationWithAKeyValue(SomeKeyName.FOO, "bar");
     *
     * // assertions fail - assuming that there are no observations with such a key value
     * assertThat(testObservationRegistry).hasAnObservationWithAKeyValue(SomeKeyName.FOO, "baz");</code></pre>
     * @param key expected key name
     * @param value expected key value
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no Observation with given key name and value
     */
    public TestObservationRegistryAssert hasAnObservationWithAKeyValue(KeyName key, String value) {
        return hasAnObservationWithAKeyValue(key.asString(), value);
    }

    /**
     * Verifies that there is an Observation with a key name.
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).hasAnObservationWithAKeyName(SomeKeyName.FOO);
     *
     * // assertions fail - assuming that there are no observation with such a key name
     * assertThat(testObservationRegistry).hasAnObservationWithAKeyName(SomeKeyName.FOO);</code></pre>
     * @param key expected key name
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no Observation with given key name
     */
    public TestObservationRegistryAssert hasAnObservationWithAKeyName(KeyName key) {
        return hasAnObservationWithAKeyName(key.asString());
    }

    /**
     * Provides verification for all Observations.
     * <p>
     * Examples: <pre><code class='java'>
     * // assertions succeed
     * assertThat(testObservationRegistry).hasAnObservation(observationContextAssert -&gt; observationContextAssert.hasNameEqualTo("foo").hasError());
     *
     * // assertions fail - assuming that there was a foo observation but none had errors
     * assertThat(testObservationRegistry).hasAnObservation(observationContextAssert -&gt; observationContextAssert.hasNameEqualTo("foo").hasError());</code></pre>
     * @param observationConsumer assertion to be executed for each Observation
     * @return {@code this} assertion object.
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no Observation that passes the assertion
     * @since 1.11.0
     */
    public TestObservationRegistryAssert hasAnObservation(Consumer<ObservationContextAssert> observationConsumer) {
        isNotNull();
        Queue<TestObservationRegistry.TestObservationContext> contexts = this.actual.getContexts();
        for (TestObservationRegistry.TestObservationContext context : contexts) {
            try {
                observationConsumer.accept(ObservationContextAssert.then(context.getContext()));
                return this;
            }
            catch (AssertionError error) {
                // ignore
            }
        }
        failWithMessage(
                "There should be at least one Observation that matches the assertion. Found following Observations:\n%s",
                observations());
        return this;
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
         * Syntactic sugar to smoothly go to
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

        /**
         * Verifies that the {@link Observation} has an event with the given name.
         * @param name event name
         * @return this
         * @throws AssertionError if the {@link Observation} does not have an event with
         * the given name
         * @since 1.15.0
         */
        public TestObservationRegistryAssertReturningObservationContextAssert hasEvent(String name) {
            isNotNull();
            if (!this.testContext.hasEvent(name)) {
                failWithMessage("Observation should have an event with name <%s>", name);
            }
            return this;
        }

        /**
         * Verifies that the {@link Observation} has an event with the given name and
         * contextual name.
         * @param name event name
         * @param contextualName contextual name
         * @return this
         * @throws AssertionError if the {@link Observation} does not have an event with
         * the given name and contextual name
         * @since 1.15.0
         */
        public TestObservationRegistryAssertReturningObservationContextAssert hasEvent(String name,
                String contextualName) {
            isNotNull();
            if (!this.testContext.hasEvent(name, contextualName)) {
                failWithMessage("Observation should have an event with name <%s> and contextual name <%s>", name,
                        contextualName);
            }
            return this;
        }

        /**
         * Verifies that the {@link Observation} does not have an event with the given
         * name.
         * @param name event name
         * @return this
         * @throws AssertionError if the {@link Observation} has an event with the given
         * name
         * @since 1.15.0
         */
        public TestObservationRegistryAssertReturningObservationContextAssert doesNotHaveEvent(String name) {
            isNotNull();
            if (this.testContext.hasEvent(name)) {
                failWithMessage("Observation should not have an event with name <%s>", name);
            }
            return this;
        }

        /**
         * Verifies that the {@link Observation} does not have an event with the given
         * name and contextual name.
         * @param name event name
         * @param contextualName contextual name
         * @return this
         * @throws AssertionError if the {@link Observation} has an event with the given
         * name and contextual name
         * @since 1.15.0
         */
        public TestObservationRegistryAssertReturningObservationContextAssert doesNotHaveEvent(String name,
                String contextualName) {
            isNotNull();
            if (this.testContext.hasEvent(name, contextualName)) {
                failWithMessage("Observation should not have an event with name <%s> and contextual name <%s>", name,
                        contextualName);
            }
            return this;
        }

    }

}
