/**
 * Copyright 2021 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.tck;

import java.time.Duration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.TimerRecordingHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Base class for {@link TimerRecordingHandler} compatibility tests that support a concrete type of context only.
 * To run a {@link TimerRecordingHandler} implementation against this TCK, make a test class that extends this
 * and implement the abstract methods.
 *
 * @author Marcin Grzejszczak
 */
public abstract class ConcreteHandlerContextTimerRecordingHandlerCompatibilityKit<T extends Timer.HandlerContext> {

    protected TimerRecordingHandler<T> handler;

    protected MeterRegistry meterRegistry = new SimpleMeterRegistry();

    public abstract TimerRecordingHandler<T> handler();

    protected Timer.Sample sample = Timer.start(meterRegistry);

    public abstract T context();

    @BeforeEach
    void setup() {
        // assigned here rather than at initialization so subclasses can use fields in their registry() implementation
        handler = handler();
    }

    @Test
    @DisplayName("compatibility test provides a null accepting context timer recording handler")
    void handlerSupportsConcreteContextForHandlerMethods() {
        assertThatCode(() -> handler.onStart(sample, context())).doesNotThrowAnyException();
        assertThatCode(() -> handler.onStop(sample, context(), Timer.builder("timer for concrete context")
                .register(meterRegistry), Duration.ofSeconds(1L))).doesNotThrowAnyException();
        assertThatCode(() -> handler.onError(sample, context(), new RuntimeException())).doesNotThrowAnyException();
        assertThatCode(() -> handler.onScopeOpened(sample, context())).doesNotThrowAnyException();
    }

    @Test
    void handlerSupportsConcreteContextOnly() {
        assertThatCode(() -> handler.supportsContext(context())).doesNotThrowAnyException();
        assertThat(handler.supportsContext(context())).as("Handler supports only concrete context").isTrue();

        assertThatCode(() -> handler.supportsContext(null)).doesNotThrowAnyException();
        assertThat(handler.supportsContext(null)).as("Handler supports only concrete context - no nulls accepted").isFalse();

        assertThatCode(() -> handler.supportsContext(new NotMatchingHandlerContext())).doesNotThrowAnyException();
        assertThat(handler.supportsContext(new NotMatchingHandlerContext())).as("Handler supports only concrete context").isFalse();
    }

    static class NotMatchingHandlerContext extends Timer.HandlerContext {

    }
}

