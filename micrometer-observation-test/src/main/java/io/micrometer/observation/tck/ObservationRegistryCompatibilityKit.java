/*
 * Copyright 2022 VMware, Inc.
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
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

/**
 * Base class for {@link ObservationRegistry} compatibility tests. To run a
 * {@link ObservationRegistry} implementation against this TCK, make a test class that
 * extends this and implement the abstract methods.
 *
 * @author Jonatan Ivanov
 * @author Marcin Grzejszczak
 */
public abstract class ObservationRegistryCompatibilityKit {

    private ObservationRegistry registry;

    public abstract ObservationRegistry registry();

    @BeforeEach
    void setup() {
        // assigned here rather than at initialization so subclasses can use fields in
        // their registry() implementation
        registry = registry();
    }

    @Test
    @DisplayName("observe using handlers")
    void observeWithHandlers() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handlerThatHandlesNothing = mock(ObservationHandler.class);
        registry.observationConfig().observationHandler(handler);
        registry.observationConfig().observationHandler(handlerThatHandlesNothing);
        when(handler.supportsContext(any())).thenReturn(true);
        when(handlerThatHandlesNothing.supportsContext(any())).thenReturn(false);

        Observation observation = Observation.start("myObservation", registry);
        InOrder inOrder = inOrder(handler, handlerThatHandlesNothing);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handlerThatHandlesNothing).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        verifyNoMoreInteractions(handlerThatHandlesNothing);

        try (Observation.Scope scope = observation.openScope()) {
            inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
            assertThat(scope.getCurrentObservation()).isSameAs(observation);

            Observation.Event event = Observation.Event.of("testEvent", "event for testing");
            observation.event(event);
            Observation.Event event2 = Observation.Event.of("testEvent", "event for testing",
                    System.currentTimeMillis());
            observation.event(event2);

            inOrder.verify(handler).onEvent(same(event), isA(Observation.Context.class));
            inOrder.verify(handler).onEvent(same(event2), isA(Observation.Context.class));

            Throwable exception = new IOException("simulated");
            observation.error(exception);
            inOrder.verify(handler).onError(isA(Observation.Context.class));
        }
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        observation.stop();
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void runnableShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Runnable runnable = () -> assertThat(registry.getCurrentObservation()).isSameAs(observation);
        observation.observe(runnable);
        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void wrappedRunnableShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Runnable runnable = () -> assertThat(registry.getCurrentObservation()).isSameAs(observation);
        observation.wrap(runnable).run();
        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void runnableThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Runnable runnable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new RuntimeException("simulated");
        };
        assertThatThrownBy(() -> observation.observe(runnable)).isInstanceOf(RuntimeException.class)
            .hasMessage("simulated")
            .hasNoCause();

        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void wrappedRunnableThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Runnable runnable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new RuntimeException("simulated");
        };
        assertThatThrownBy(() -> observation.wrap(runnable).run()).isInstanceOf(RuntimeException.class)
            .hasMessage("simulated")
            .hasNoCause();

        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void checkedRunnableShouldBeObserved() throws Throwable {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedRunnable<Throwable> checkedRunnable = () -> assertThat(registry.getCurrentObservation())
            .isSameAs(observation);
        observation.observeChecked(checkedRunnable);
        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void wrappedCheckedRunnableShouldBeObserved() throws Throwable {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedRunnable<Throwable> checkedRunnable = () -> assertThat(registry.getCurrentObservation())
            .isSameAs(observation);
        observation.wrapChecked(checkedRunnable).run();
        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void checkedRunnableThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedRunnable<IOException> checkedRunnable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new IOException("simulated");
        };
        assertThatThrownBy(() -> observation.observeChecked(checkedRunnable)).isInstanceOf(IOException.class)
            .hasMessage("simulated")
            .hasNoCause();

        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void wrappedCheckedRunnableThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedRunnable<IOException> checkedRunnable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new IOException("simulated");
        };
        assertThatThrownBy(() -> observation.wrapChecked(checkedRunnable).run()).isInstanceOf(IOException.class)
            .hasMessage("simulated")
            .hasNoCause();

        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void supplierShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Supplier<String> supplier = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            return "test";
        };
        String result = observation.observe(supplier);
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void wrappedSupplierShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Supplier<String> supplier = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            return "test";
        };
        String result = observation.wrap(supplier).get();
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void supplierThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Supplier<String> supplier = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new RuntimeException("simulated");
        };
        assertThatThrownBy(() -> observation.observe(supplier)).isInstanceOf(RuntimeException.class)
            .hasMessage("simulated")
            .hasNoCause();

        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void wrappedSupplierThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Supplier<String> supplier = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new RuntimeException("simulated");
        };
        assertThatThrownBy(() -> observation.wrap(supplier).get()).isInstanceOf(RuntimeException.class)
            .hasMessage("simulated")
            .hasNoCause();

        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void callableShouldBeObserved() throws Throwable {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedCallable<String, Throwable> callable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            return "test";
        };
        String result = observation.observeChecked(callable);
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void wrappedCallableShouldBeObserved() throws Throwable {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedCallable<String, Throwable> callable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            return "test";
        };
        String result = observation.wrapChecked(callable).call();
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void callableThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedCallable<String, IOException> callable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new IOException("simulated");
        };
        assertThatThrownBy(() -> observation.observeChecked(callable)).isInstanceOf(IOException.class)
            .hasMessage("simulated")
            .hasNoCause();

        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void wrappedCallableThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedCallable<String, IOException> callable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new IOException("simulated");
        };
        assertThatThrownBy(() -> observation.wrapChecked(callable).call()).isInstanceOf(IOException.class)
            .hasMessage("simulated")
            .hasNoCause();

        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void functionShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Function<Observation.Context, String> function = (context) -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            assertThat(context).isSameAs(observation.getContext());
            return "test";
        };
        String result = observation.observeWithContext(function);
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void functionThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Function<Observation.Context, String> function = (context) -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new RuntimeException("simulated");
        };
        assertThatThrownBy(() -> observation.observeWithContext(function)).isInstanceOf(RuntimeException.class)
            .hasMessage("simulated")
            .hasNoCause();

        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void checkedFunctionShouldBeObserved() throws Throwable {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedFunction<Observation.Context, String, Throwable> function = (context) -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            assertThat(context).isSameAs(observation.getContext());
            return "test";
        };
        String result = observation.observeCheckedWithContext(function);
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void checkedFunctionThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedFunction<Observation.Context, String, Throwable> function = (context) -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new IOException("simulated");
        };
        assertThatThrownBy(() -> observation.observeCheckedWithContext(function)).isInstanceOf(IOException.class)
            .hasMessage("simulated")
            .hasNoCause();

        assertThat(registry.getCurrentObservation()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).supportsContext(isA(Observation.Context.class));
        inOrder.verify(handler).onStart(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler).onStop(isA(Observation.Context.class));
    }

    @Test
    void runnableShouldBeScoped() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.start("myObservation", registry);
        Runnable runnable = () -> assertThat(registry.getCurrentObservation()).isSameAs(observation);
        observation.scoped(runnable);
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(observation.getContext().getError()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onStop(isA(Observation.Context.class));
    }

    @Test
    void errorShouldBeReportedOnFailingScopedRunnable() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.start("myObservation", registry);
        RuntimeException error = new RuntimeException("simulated");

        Runnable runnable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw error;
        };
        assertThatThrownBy(() -> observation.scoped(runnable)).isSameAs(error);
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(observation.getContext().getError()).isSameAs(error);

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onStop(isA(Observation.Context.class));
    }

    @Test
    void checkedRunnableShouldBeScoped() throws Throwable {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.start("myObservation", registry);
        Observation.CheckedRunnable<Throwable> checkedRunnable = () -> assertThat(registry.getCurrentObservation())
            .isSameAs(observation);
        observation.scopedChecked(checkedRunnable);
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(observation.getContext().getError()).isNull();

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onStop(isA(Observation.Context.class));
    }

    @Test
    void errorShouldBeReportedOnFailingScopedCheckedRunnable() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.start("myObservation", registry);
        RuntimeException error = new RuntimeException("simulated");

        Observation.CheckedRunnable<Throwable> checkedRunnable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw error;
        };
        assertThatThrownBy(() -> observation.scopedChecked(checkedRunnable)).isSameAs(error);
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(observation.getContext().getError()).isSameAs(error);

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onStop(isA(Observation.Context.class));
    }

    @Test
    void supplierShouldBeScoped() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.start("myObservation", registry);
        Supplier<String> supplier = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            return "test";
        };
        String result = observation.scoped(supplier);
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(result).isEqualTo("test");

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onStop(isA(Observation.Context.class));
    }

    @Test
    void errorShouldBeReportedOnFailingScopedSupplier() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.start("myObservation", registry);
        RuntimeException error = new RuntimeException("simulated");

        Supplier<String> supplier = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw error;
        };
        assertThatThrownBy(() -> observation.scoped(supplier)).isSameAs(error);
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(observation.getContext().getError()).isSameAs(error);

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onStop(isA(Observation.Context.class));
    }

    @Test
    void checkedCallableShouldBeScoped() throws IOException {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.start("myObservation", registry);
        Observation.CheckedCallable<String, IOException> callable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            return "test";
        };
        String result = observation.scopedChecked(callable);
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(result).isEqualTo("test");

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onError(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onStop(isA(Observation.Context.class));
    }

    @Test
    void errorShouldBeReportedOnFailingScopedCheckedCallable() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.start("myObservation", registry);
        RuntimeException error = new RuntimeException("simulated");

        Observation.CheckedCallable<String, RuntimeException> callable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw error;
        };
        assertThatThrownBy(() -> observation.scopedChecked(callable)).isSameAs(error);
        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(observation.getContext().getError()).isSameAs(error);

        InOrder inOrder = inOrder(handler);
        inOrder.verify(handler).onScopeOpened(isA(Observation.Context.class));
        inOrder.verify(handler).onScopeClosed(isA(Observation.Context.class));
        inOrder.verify(handler).onError(isA(Observation.Context.class));
        inOrder.verify(handler, times(0)).onStop(isA(Observation.Context.class));
    }

    @Test
    void runnableShouldBeParentScoped() {
        registry.observationConfig().observationHandler(c -> true);
        Observation parent = Observation.start("myObservation", registry);
        Runnable runnable = () -> assertThat(registry.getCurrentObservation()).isSameAs(parent);
        Observation.tryScoped(parent, runnable);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void runnableShouldNotBeParentScopedIfParentIsNull() {
        Runnable runnable = () -> assertThat(registry.getCurrentObservation()).isNull();
        Observation.tryScoped(null, runnable);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void checkedRunnableShouldBeParentScoped() throws Throwable {
        registry.observationConfig().observationHandler(c -> true);
        Observation parent = Observation.start("myObservation", registry);
        Observation.CheckedRunnable<Throwable> checkedRunnable = () -> assertThat(registry.getCurrentObservation())
            .isSameAs(parent);
        Observation.tryScopedChecked(parent, checkedRunnable);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void checkedRunnableShouldNotBeParentScopedIfParentIsNull() throws Throwable {
        Observation.CheckedRunnable<Throwable> checkedRunnable = () -> assertThat(registry.getCurrentObservation())
            .isNull();
        Observation.tryScopedChecked(null, checkedRunnable);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void supplierShouldBeParentScoped() {
        registry.observationConfig().observationHandler(c -> true);
        Observation parent = Observation.start("myObservation", registry);
        Supplier<String> supplier = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(parent);
            return "test";
        };
        String result = Observation.tryScoped(parent, supplier);
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void supplierShouldNotBeParentScopedIfParentIsNull() {
        Supplier<String> supplier = () -> {
            assertThat(registry.getCurrentObservation()).isNull();
            return "test";
        };
        Observation.tryScoped(null, supplier);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void checkedCallableShouldBeParentScoped() throws Throwable {
        registry.observationConfig().observationHandler(c -> true);
        Observation parent = Observation.start("myObservation", registry);
        Observation.CheckedCallable<String, Throwable> callable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(parent);
            return "test";
        };
        String result = Observation.tryScopedChecked(parent, callable);
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void checkedCallableShouldNotBeParentScopedIfParentIsNull() throws Throwable {
        Observation.CheckedCallable<String, Throwable> callable = () -> {
            assertThat(registry.getCurrentObservation()).isNull();
            return "test";
        };
        Observation.tryScopedChecked(null, callable);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void observationFieldsShouldBeSetOnContext() {
        AssertingHandler assertingHandler = new AssertingHandler();
        registry.observationConfig()
            .observationConvention(new TestObservationConvention("global"))
            .observationConvention(new UnsupportedObservationConvention("global"))
            .observationHandler(assertingHandler);

        TestContext testContext = new TestContext();
        testContext.put("context.field", "42");
        Exception exception = new IOException("simulated");
        Observation observation = Observation.createNotStarted("test.observation", () -> testContext, registry)
            .lowCardinalityKeyValue("lcTag1", "0")
            // should override the previous line
            .lowCardinalityKeyValue("lcTag1", "1")
            .lowCardinalityKeyValues(KeyValues.of("lcTag2", "2"))
            .highCardinalityKeyValue("hcTag1", "0")
            // should override the previous line
            .highCardinalityKeyValue("hcTag1", "3")
            .highCardinalityKeyValues(KeyValues.of("hcTag2", "4"))
            .contextualName("test.observation.42")
            .error(exception)
            .start();
        observation.stop();

        assertingHandler.checkAssertions(context -> {
            assertThat(context).isSameAs(testContext);
            assertThat(context.getName()).isEqualTo("test.observation");
            assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("lcTag1", "1"),
                    KeyValue.of("lcTag2", "2"), KeyValue.of("global.context.class", "TestContext"));
            assertThat(context.getHighCardinalityKeyValues()).containsExactlyInAnyOrder(KeyValue.of("hcTag1", "3"),
                    KeyValue.of("hcTag2", "4"), KeyValue.of("global.uuid", testContext.uuid));

            assertThat(context.getAllKeyValues()).containsExactlyInAnyOrder(KeyValue.of("lcTag1", "1"),
                    KeyValue.of("lcTag2", "2"), KeyValue.of("global.context.class", "TestContext"),
                    KeyValue.of("hcTag1", "3"), KeyValue.of("hcTag2", "4"),
                    KeyValue.of("global.uuid", testContext.uuid));

            assertThat((String) context.get("context.field")).isEqualTo("42");

            assertThat(context.getContextualName()).isEqualTo("test.observation.42");
            assertThat(context.getError()).isSameAs(exception);

            assertThat(context.toString()).containsOnlyOnce("name='test.observation'")
                .containsOnlyOnce("contextualName='test.observation.42'")
                .containsOnlyOnce("error='java.io.IOException: simulated'")
                .containsOnlyOnce(
                        "lowCardinalityKeyValues=[global.context.class='TestContext', lcTag1='1', lcTag2='2']")
                .containsOnlyOnce(
                        "highCardinalityKeyValues=[global.uuid='" + testContext.uuid + "', hcTag1='3', hcTag2='4']")
                .containsOnlyOnce("map=[context.field='42']");
        });
    }

    @Test
    void globallyOverriddenNameAndContextualNameShouldBeSetOnContext() {
        AssertingHandler assertingHandler = new AssertingHandler();
        registry.observationConfig()
            .observationConvention(new TestObservationConventionWithNameOverrides())
            .observationHandler(assertingHandler);

        TestContext testContext = new TestContext();
        Observation observation = Observation.createNotStarted("test.observation", () -> testContext, registry)
            .contextualName("test.observation.42")
            .start();
        observation.stop();

        assertingHandler.checkAssertions(context -> {
            assertThat(context.getName()).isEqualTo("conventionOverriddenName");
            assertThat(context.getContextualName()).isEqualTo("conventionOverriddenContextualName");
            assertThat(context.toString()).containsOnlyOnce("name='conventionOverriddenName'")
                .containsOnlyOnce("contextualName='conventionOverriddenContextualName'")
                .containsOnlyOnce("error='null'")
                .containsOnlyOnce("lowCardinalityKeyValues=[]")
                .containsOnlyOnce("highCardinalityKeyValues=[]")
                .containsOnlyOnce("map=[]");
        });
    }

    @Test
    void locallyOverriddenNameAndContextualNameShouldBeSetOnContext() {
        AssertingHandler assertingHandler = new AssertingHandler();
        registry.observationConfig().observationHandler(assertingHandler);

        TestContext testContext = new TestContext();
        Observation observation = Observation.createNotStarted("test.observation", () -> testContext, registry)
            .contextualName("test.observation.42")
            .observationConvention(new TestObservationConventionWithNameOverrides())
            .start();
        observation.stop();

        assertingHandler.checkAssertions(context -> {
            assertThat(context.getName()).isEqualTo("conventionOverriddenName");
            assertThat(context.getContextualName()).isEqualTo("conventionOverriddenContextualName");
            assertThat(context.toString()).containsOnlyOnce("name='conventionOverriddenName'")
                .containsOnlyOnce("contextualName='conventionOverriddenContextualName'")
                .containsOnlyOnce("error='null'")
                .containsOnlyOnce("lowCardinalityKeyValues=[]")
                .containsOnlyOnce("highCardinalityKeyValues=[]")
                .containsOnlyOnce("map=[]");
        });
    }

    static class TestContext extends Observation.Context {

        final String uuid = UUID.randomUUID().toString();

    }

    static class TestObservationConvention implements GlobalObservationConvention<TestContext> {

        private final String id;

        public TestObservationConvention(String id) {
            this.id = id;
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(TestContext context) {
            return KeyValues.of(this.id + "." + "context.class", TestContext.class.getSimpleName());
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(TestContext context) {
            return KeyValues.of(this.id + "." + "uuid", context.uuid);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof TestContext;
        }

    }

    static class TestObservationConventionWithNameOverrides implements GlobalObservationConvention<TestContext> {

        @Nullable
        @Override
        public String getName() {
            return "conventionOverriddenName";
        }

        @Nullable
        @Override
        public String getContextualName(TestContext context) {
            return "conventionOverriddenContextualName";
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof TestContext;
        }

    }

    static class UnsupportedObservationConvention implements GlobalObservationConvention<Observation.Context> {

        private final String id;

        public UnsupportedObservationConvention(String id) {
            this.id = id;
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of(this.id + "." + "unsupported.lc", "unsupported");
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of(this.id + "." + "unsupported.hc", "unsupported");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return false;
        }

    }

    static class AssertingHandler implements ObservationHandler<Observation.Context> {

        private boolean stopped = false;

        @Nullable
        private Observation.Context context = null;

        @Override
        public void onStop(Observation.Context context) {
            this.stopped = true;
            this.context = context;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        public void checkAssertions(Consumer<Observation.Context> checker) {
            assertThat(this.stopped).isTrue();
            assertThat(this.context).isNotNull();
            checker.accept(this.context);
        }

    }

}
