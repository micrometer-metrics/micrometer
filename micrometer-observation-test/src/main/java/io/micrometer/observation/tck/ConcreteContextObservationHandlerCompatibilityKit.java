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
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Base class for {@link ObservationHandler} compatibility tests that support a concrete
 * type of context only. To run an {@link ObservationHandler} implementation against this
 * TCK, make a test class that extends this and implement the abstract methods.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public abstract class ConcreteContextObservationHandlerCompatibilityKit<T extends Observation.Context> {

    protected ObservationHandler<T> handler;

    protected ObservationRegistry observationRegistry = ObservationRegistry.create();

    public abstract ObservationHandler<T> handler();

    protected Observation sample = Observation.createNotStarted("hello", observationRegistry);

    public abstract T context();

    @BeforeEach
    void setup() {
        // assigned here rather than at initialization so subclasses can use fields in
        // their registry() implementation
        handler = handler();
    }

    @Test
    @DisplayName("compatibility test provides a concrete context accepting observation handler")
    void handlerSupportsConcreteContextForHandlerMethods() {
        assertThatCode(() -> handler.onStart(context())).doesNotThrowAnyException();
        assertThatCode(() -> handler.onStop(context())).doesNotThrowAnyException();
        assertThatCode(() -> handler.onError(context())).doesNotThrowAnyException();
        assertThatCode(() -> handler.onEvent(Observation.Event.of("testEvent"), context())).doesNotThrowAnyException();
        assertThatCode(() -> handler.onScopeOpened(context())).doesNotThrowAnyException();
        assertThatCode(() -> handler.onScopeClosed(context())).doesNotThrowAnyException();
        assertThatCode(() -> handler.onScopeReset(context())).doesNotThrowAnyException();
    }

    @Test
    void handlerSupportsConcreteContextOnly() {
        assertThatCode(() -> handler.supportsContext(context())).doesNotThrowAnyException();
        assertThat(handler.supportsContext(context())).as("Handler supports only concrete context").isTrue();

        assertThatCode(() -> handler.supportsContext(null)).doesNotThrowAnyException();
        assertThat(handler.supportsContext(null)).as("Handler supports only concrete context - no nulls accepted")
            .isFalse();

        assertThatCode(() -> handler.supportsContext(new NotMatchingContext())).doesNotThrowAnyException();
        assertThat(handler.supportsContext(new NotMatchingContext())).as("Handler supports only concrete context")
            .isFalse();
    }

    static class NotMatchingContext extends Observation.Context {

    }

}
