/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.observation.contextpropagation;

import io.micrometer.context.ContextAccessor;
import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.NullObservation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenNoException;

class ObservationThreadLocalAccessorTests {

    static ObservationRegistry globalObservationRegistry = ObservationRegistry.create();

    static MapContextAccessor mapContextAccessor = new MapContextAccessor();

    ExecutorService executorService = Executors.newSingleThreadExecutor();

    ObservationRegistry observationRegistry = ObservationRegistry.create();

    ObservationRegistry observationRegistry2 = ObservationRegistry.create();

    ContextRegistry registry = new ContextRegistry();

    @BeforeAll
    static void beforeAll() {
        ContextRegistry.getInstance().registerContextAccessor(mapContextAccessor);
    }

    @BeforeEach
    void setup() {
        registry.registerThreadLocalAccessor(new ObservationThreadLocalAccessor());
    }

    @AfterEach
    void clean() {
        executorService.shutdown();
    }

    @Test
    void capturedThreadLocalValuesShouldBeCapturedRestoredAndCleared()
            throws InterruptedException, ExecutionException, TimeoutException {
        observationRegistry.observationConfig().observationHandler(new TracingHandler());

        // given
        Observation parent = Observation.start("parent", observationRegistry);
        Observation other1 = Observation.start("other1", observationRegistry);
        Observation other2 = Observation.start("other2", observationRegistry);
        Observation child = Observation.createNotStarted("foo", observationRegistry).parentObservation(parent).start();
        thenCurrentObservationIsNull();

        try (Observation.Scope scope = child.openScope()) {
            thenCurrentObservationHasParent(parent, child);
            thenCurrentScopeHasParent(null);
            then(child.getEnclosingScope()).isSameAs(scope);
        }
        then(child.getEnclosingScope()).isNull();
        thenCurrentObservationIsNull();

        try (Observation.Scope scope = other1.openScope()) {
            try (Observation.Scope scope2 = child.openScope()) {
                thenCurrentObservationHasParent(parent, child);
                then(child.getEnclosingScope()).isSameAs(scope2);
            }
            then(child.getEnclosingScope()).isNull();
            then(other1.getEnclosingScope()).isSameAs(scope);
        }

        then(child.getEnclosingScope()).isNull();
        thenCurrentObservationIsNull();

        // when context captured
        ContextSnapshot container;
        try (Observation.Scope scope = other2.openScope()) {
            try (Observation.Scope scope2 = child.openScope()) {
                thenCurrentObservationHasParent(parent, child);
                then(child.getEnclosingScope()).isSameAs(scope2);
                container = ContextSnapshotFactory.builder()
                    .captureKeyPredicate(key -> true)
                    .contextRegistry(registry)
                    .build()
                    .captureAll();
            }
        }

        then(child.getEnclosingScope()).isNull();
        thenCurrentObservationIsNull();

        // when first scope created
        try (ContextSnapshot.Scope scope = container.setThreadLocals()) {
            thenCurrentObservationHasParent(parent, child);
            Scope inScope = thenCurrentScopeHasParent(null);
            then(child.getEnclosingScope()).isNotNull();

            // when second, nested scope created
            try (ContextSnapshot.Scope scope2 = container.setThreadLocals()) {
                then(child.getEnclosingScope()).isNotNull();
                thenCurrentObservationHasParent(parent, child);
                Scope inSecondScope = thenCurrentScopeHasParent(inScope);

                // when context gets propagated to a new thread
                ContextExecutorService.wrap(executorService, () -> container).submit(() -> {
                    thenCurrentObservationHasParent(parent, child);
                    thenCurrentScopeHasParent(null);
                }).get(5, TimeUnit.SECONDS);

                then(scopeParent(inSecondScope)).isSameAs(inScope);

                // when we're going through the null scope when there was a previous value
                // in TL
                ContextSnapshot withClearing = ContextSnapshotFactory.builder()
                    .clearMissing(true)
                    .build()
                    .captureFrom(new HashMap<>());
                withClearing.wrap(this::thenCurrentObservationIsNullObservation).run();

                then(child.getEnclosingScope()).isNotNull();
                thenCurrentObservationHasParent(parent, child);
            }
            then(scopeParent(inScope)).isNull();
        }

        thenCurrentObservationIsNull();

        then(child.getEnclosingScope()).isNull();
        then(parent.getEnclosingScope()).isNull();

        // when we're going through the null scope when there was no previous value in TL
        ContextSnapshot withClearing = ContextSnapshotFactory.builder()
            .clearMissing(true)
            .build()
            .captureFrom(new HashMap<>());
        withClearing.wrap(this::thenCurrentObservationIsNull).run();

        then(child.getEnclosingScope()).isNull();
        then(parent.getEnclosingScope()).isNull();

        child.stop();
        parent.stop();

        then(child.getEnclosingScope()).isNull();
        then(parent.getEnclosingScope()).isNull();
    }

    @Test
    void uses2DifferentObservationRegistries() {
        AtomicReference<String> calledHandler = new AtomicReference<>("");

        TestHandler testHandlerForOR1 = new TestHandler("handler 1", calledHandler);
        observationRegistry.observationConfig().observationHandler(testHandlerForOR1);
        TestHandler testHandlerForOR2 = new TestHandler("handler 2", calledHandler);
        observationRegistry2.observationConfig().observationHandler(testHandlerForOR2);

        // given
        Observation parent = Observation.start("parent", observationRegistry);
        Observation child = Observation.createNotStarted("child", observationRegistry)
            .parentObservation(parent)
            .start();
        thenCurrentObservationIsNull();

        Observation parent2 = Observation.start("parent2", observationRegistry2);
        Observation child2 = Observation.createNotStarted("child2", observationRegistry2)
            .parentObservation(parent2)
            .start();
        thenCurrentObservationIsNull();

        try (Observation.Scope scope = child.openScope()) {
            try (Observation.Scope scope2 = child2.openScope()) {
                thenCurrentObservationHasParent(parent2, child2);
            }
            then(calledHandler.get()).isEqualTo("handler 2");
        }
        then(calledHandler.get()).isEqualTo("handler 1");

        then(child.getEnclosingScope()).isNull();
        thenCurrentObservationIsNull();

        child.stop();
        child2.stop();
        parent.stop();
        parent2.stop();

        thenCurrentObservationIsNull();
    }

    @Test
    void acceptsANullCurrentScopeOnRestore() {
        observationRegistry.observationConfig().observationHandler(new TracingHandler());

        thenNoException().isThrownBy(() -> new ObservationThreadLocalAccessor() {
            @Override
            void assertFalse(String message) {

            }
        }.restore(null));
    }

    private void thenCurrentObservationHasParent(Observation parent, Observation observation) {
        then(globalObservationRegistry.getCurrentObservation()).isSameAs(observation);
        then(globalObservationRegistry.getCurrentObservation().getContextView().getParentObservation())
            .isSameAs(parent);
    }

    private Scope thenCurrentScopeHasParent(Scope first) {
        Scope inScope = TracingHandler.value.get();
        then(scopeParent(inScope)).isSameAs(first);
        return inScope;
    }

    private void thenCurrentObservationIsNull() {
        then(ObservationRegistry.create().getCurrentObservation()).isNull();
        then(TracingHandler.value.get()).isNull();
    }

    private void thenCurrentObservationIsNullObservation() {
        then(globalObservationRegistry.getCurrentObservation()).isInstanceOf(NullObservation.class);
        then(TracingHandler.value.get()).isNotNull();
        then(TracingHandler.value.get().observationName).isEqualTo("null");
    }

    private Scope scopeParent(Scope scope) {
        return scope.parent();
    }

    static class TracingHandler implements ObservationHandler<Observation.Context> {

        static final ThreadLocal<Scope> value = new ThreadLocal<>();

        @Override
        public void onStart(Observation.Context context) {
            System.out.println("on start [" + context + "]");
        }

        @Override
        public void onScopeOpened(Observation.Context context) {
            Scope currentScope = value.get();
            Scope newScope = new Scope(currentScope, context.getName());
            context.put(Scope.class, newScope);
            System.out.println("\ton open scope [" + context + "]. Hashcode [" + newScope + "]");
        }

        @Override
        public void onScopeClosed(Observation.Context context) {
            Scope scope = context.get(Scope.class);
            scope.close();
            System.out.println("\ton scope close [" + context + "]. Hashcode [" + scope + "]");
        }

        @Override
        public void onStop(Observation.Context context) {
            context.put("state", "stopped");
            System.out.println("on stop [" + context.getName() + "]");
        }

        @Override
        public void onScopeReset(Observation.Context context) {
            value.remove();
            System.out.println("on reset [" + context.getName() + "]");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class TestHandler implements ObservationHandler<Observation.Context> {

        final String name;

        final AtomicReference<String> reference;

        TestHandler(String name, AtomicReference<String> reference) {
            this.name = name;
            this.reference = reference;
        }

        @Override
        public void onScopeClosed(Context context) {
            this.reference.set(name);
        }

        @Override
        public boolean supportsContext(Context context) {
            return true;
        }

    }

    static class Scope implements Closeable {

        private final Scope previous;

        private final String observationName;

        Scope(Scope previous, String observationName) {
            this.previous = previous;
            this.observationName = observationName;
            TracingHandler.value.set(this);
        }

        @Override
        public void close() {
            TracingHandler.value.set(previous);
        }

        Scope parent() {
            return this.previous;
        }

        @Override
        public String toString() {
            return "Scope{" + "previous=" + previous + ", observationName='" + observationName + '\'' + '}';
        }

    }

    static class MapContextAccessor implements ContextAccessor<Map, Map> {

        @Override
        public Class<? extends Map> readableType() {
            return Map.class;
        }

        @Override
        public void readValues(Map sourceContext, Predicate<Object> keyPredicate, Map<Object, Object> readValues) {

        }

        @Override
        public <T> T readValue(Map sourceContext, Object key) {
            return (T) sourceContext.get(key);
        }

        @Override
        public Class<? extends Map> writeableType() {
            return Map.class;
        }

        @Override
        public Map writeValues(Map<Object, Object> valuesToWrite, Map targetContext) {
            targetContext.putAll(valuesToWrite);
            return targetContext;
        }

    }

}
