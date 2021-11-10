/**
 * Copyright 2017 VMware, Inc.
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
import io.micrometer.core.instrument.TimerRecordingListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Base class for {@link TimerRecordingListener} compatibility tests that support a concrete type of context only.
 * To run a {@link TimerRecordingListener} implementation against this TCK, make a test class that extends this
 * and implement the abstract methods.
 *
 * @author Marcin Grzejszczak
 */
public abstract class ConcreteContextTimerRecordingListenerCompatibilityKit<T extends Timer.Context> {

    protected TimerRecordingListener<T> listener;

    protected MeterRegistry meterRegistry = new SimpleMeterRegistry();

    public abstract TimerRecordingListener<T> listener();

    protected Timer.Sample sample = Timer.start(meterRegistry);

    public abstract T context();

    @BeforeEach
    void setup() {
        // assigned here rather than at initialization so subclasses can use fields in their registry() implementation
        listener = listener();
    }

    @Test
    @DisplayName("compatibility test provides a null accepting context timer recording listener")
    void listenerSupportsConcreteContextForListenerMethods() {
        assertThatCode(() -> listener.onStart(sample, context())).doesNotThrowAnyException();
        assertThatCode(() -> listener.onStop(sample, context(), Timer.builder("timer for concrete context")
                .register(meterRegistry), Duration.ofSeconds(1L))).doesNotThrowAnyException();
        assertThatCode(() -> listener.onError(sample, context(), new RuntimeException())).doesNotThrowAnyException();
        assertThatCode(() -> listener.onRestore(sample, context())).doesNotThrowAnyException();
    }

    @Test
    void listenerSupportsConcreteContextOnly() {
        assertThatCode(() -> listener.supportsContext(context())).doesNotThrowAnyException();
        assertThat(listener.supportsContext(context())).as("Listener supports only concrete context").isTrue();

        assertThatCode(() -> listener.supportsContext(null)).doesNotThrowAnyException();
        assertThat(listener.supportsContext(null)).as("Listener supports only concrete context - no nulls accepted").isFalse();

        assertThatCode(() -> listener.supportsContext(new NotMatchingContext())).doesNotThrowAnyException();
        assertThat(listener.supportsContext(new NotMatchingContext())).as("Listener supports only concrete context").isFalse();
    }

    static class NotMatchingContext extends Timer.Context {

    }
}

