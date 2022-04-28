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
package io.micrometer.core.tck;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Base class for {@link ObservationRegistry} compatibility tests.
 * To run a {@link ObservationRegistry} implementation against this TCK, make a test class that extends this
 * and implement the abstract methods.
 *
 * @author Jonatan Ivanov
 * @author Marcin Grzejszczak
 */
public abstract class ObservationRegistryCompatibilityKit {

    private ObservationRegistry registry;

    public abstract ObservationRegistry registry();

    @BeforeEach
    void setup() {
        // assigned here rather than at initialization so subclasses can use fields in their registry() implementation
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
        assertThatThrownBy(() -> observation.observe(runnable))
                .isInstanceOf(RuntimeException.class)
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

        Observation.CheckedRunnable checkedRunnable = () -> assertThat(registry.getCurrentObservation()).isSameAs(observation);
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
    void checkedRunnableThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedRunnable checkedRunnable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new IOException("simulated");
        };
        assertThatThrownBy(() -> observation.observeChecked(checkedRunnable))
                .isInstanceOf(IOException.class)
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
        assertThatThrownBy(() -> observation.observe(supplier))
                .isInstanceOf(RuntimeException.class)
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

        Observation.CheckedCallable<String> callable = () -> {
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
    void callableThrowingErrorShouldBeObserved() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        when(handler.supportsContext(isA(Observation.Context.class))).thenReturn(true);
        registry.observationConfig().observationHandler(handler);
        Observation observation = Observation.createNotStarted("myObservation", registry);

        Observation.CheckedCallable<String> callable = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            throw new IOException("simulated");
        };
        assertThatThrownBy(() -> observation.observeChecked(callable))
                .isInstanceOf(IOException.class)
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
        registry.observationConfig().observationHandler(c -> true);
        Observation observation = Observation.start("myObservation", registry);
        Runnable runnable = () -> assertThat(registry.getCurrentObservation()).isSameAs(observation);
        observation.scoped(runnable);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void errorShouldBeReportedOnFailingScopedRunnable() {
        registry.observationConfig().observationHandler(c -> true);
        Observation.Context context = new Observation.Context();
        Observation observation = Observation.start("myObservation", context, registry);
        RuntimeException error = new RuntimeException("simulated");

        Runnable runnable = () -> {
            throw error;
        };
        assertThatThrownBy(() -> observation.scoped(runnable)).isSameAs(error);
        assertThat(context.getError()).containsSame(error);
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void supplierShouldBeScoped() {
        registry.observationConfig().observationHandler(c -> true);
        Observation observation = Observation.start("myObservation", registry);
        Supplier<String> supplier = () -> {
            assertThat(registry.getCurrentObservation()).isSameAs(observation);
            return "test";
        };
        String result = observation.scoped(supplier);
        assertThat(result).isEqualTo("test");
        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void errorShouldBeReportedOnFailingScopedSupplier() {
        registry.observationConfig().observationHandler(c -> true);
        Observation.Context context = new Observation.Context();
        Observation observation = Observation.start("myObservation", context, registry);
        RuntimeException error = new RuntimeException("simulated");

        Supplier<String> supplier = () -> {
            throw error;
        };
        assertThatThrownBy(() -> observation.scoped(supplier)).isSameAs(error);
        assertThat(context.getError()).containsSame(error);
        assertThat(registry.getCurrentObservation()).isNull();
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
    void observationFieldsShouldBeSetOnContext() {
        AssertingHandler assertingHandler = new AssertingHandler();
        registry.observationConfig()
                .keyValuesProvider(new TestKeyValuesProvider("global"))
                .keyValuesProvider(new UnsupportedKeyValuesProvider("global"))
                .observationHandler(assertingHandler);

        TestContext testContext = new TestContext();
        testContext.put("context.field", "42");
        Exception exception = new IOException("simulated");
        Observation observation = Observation.start("test.observation", testContext, registry)
                .lowCardinalityKeyValue("lcTag1", "1")
                .lowCardinalityKeyValue(KeyValue.of("lcTag2", "2"))
                .highCardinalityKeyValue("hcTag1", "3")
                .highCardinalityKeyValue(KeyValue.of("hcTag2", "4"))
                .keyValuesProvider(new TestKeyValuesProvider("local"))
                .keyValuesProvider(new UnsupportedKeyValuesProvider("local"))
                .contextualName("test.observation.42")
                .error(exception);
        observation.stop();

        assertingHandler.checkAssertions(context -> {
            assertThat(context).isSameAs(testContext);
            assertThat(context.getName()).isEqualTo("test.observation");
            assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(
                    KeyValue.of("lcTag1", "1"),
                    KeyValue.of("lcTag2", "2"),
                    KeyValue.of("local.context.class", "TestContext"),
                    KeyValue.of("global.context.class", "TestContext")
            );
            assertThat(context.getHighCardinalityKeyValues()).containsExactlyInAnyOrder(
                    KeyValue.of("hcTag1", "3"),
                    KeyValue.of("hcTag2", "4"),
                    KeyValue.of("local.uuid", testContext.uuid),
                    KeyValue.of("global.uuid", testContext.uuid)
            );

            assertThat(context.getAllKeyValues()).containsExactlyInAnyOrder(
                    KeyValue.of("lcTag1", "1"),
                    KeyValue.of("lcTag2", "2"),
                    KeyValue.of("local.context.class", "TestContext"),
                    KeyValue.of("global.context.class", "TestContext"),
                    KeyValue.of("hcTag1", "3"),
                    KeyValue.of("hcTag2", "4"),
                    KeyValue.of("local.uuid", testContext.uuid),
                    KeyValue.of("global.uuid", testContext.uuid)
            );

            assertThat((String) context.get("context.field")).isEqualTo("42");

            assertThat(context.getContextualName()).isEqualTo("test.observation.42");
            assertThat(context.getError()).containsSame(exception);

            assertThat(context.toString())
                    .containsOnlyOnce("name='test.observation'")
                    .containsOnlyOnce("contextualName='test.observation.42'")
                    .containsOnlyOnce("error='java.io.IOException: simulated'")
                    .containsOnlyOnce("lowCardinalityKeyValues=[lcTag1='1', lcTag2='2', global.context.class='TestContext', local.context.class='TestContext']")
                    .containsOnlyOnce("highCardinalityKeyValues=[hcTag1='3', hcTag2='4', global.uuid='" + testContext.uuid + "', local.uuid='" + testContext.uuid + "']")
                    .containsOnlyOnce("map=[context.field='42']");
        });
    }

    static class TestContext extends Observation.Context {
        final String uuid = UUID.randomUUID().toString();
    }

    static class TestKeyValuesProvider implements Observation.GlobalKeyValuesProvider<TestContext> {
        private final String id;

        public TestKeyValuesProvider(String id) {
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

        public String getId() {
            return this.id;
        }
    }

    static class UnsupportedKeyValuesProvider implements Observation.GlobalKeyValuesProvider<Observation.Context> {
        private final String id;

        public UnsupportedKeyValuesProvider(String id) {
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

