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

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.assertj.core.api.BDDAssertions.then;

class ObservationThreadLocalAccessorTests {

    ExecutorService executorService = Executors.newSingleThreadExecutor();

    ObservationRegistry observationRegistry = ObservationRegistry.create();

    ContextRegistry registry = new ContextRegistry();

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(context -> true);
        registry.registerThreadLocalAccessor(new ObservationThreadLocalAccessor());
    }

    @AfterEach
    void clean() {
        executorService.shutdown();
    }

    @Test
    void capturedThreadLocalValuesShouldBeCapturedRestoredAndCleared()
            throws InterruptedException, ExecutionException, TimeoutException {

        // given
        Observation parent = Observation.start("parent", observationRegistry);
        Observation child = Observation.createNotStarted("foo", observationRegistry).parentObservation(parent).start();
        thenCurrentObservationIsNull();

        // when context captured
        ContextSnapshot container;
        try (Observation.Scope scope = child.openScope()) {
            thenCurrentObservationHasParent(parent, child);
            container = ContextSnapshot.captureAllUsing(key -> true, registry);
        }

        thenCurrentObservationIsNull();

        // when first scope created
        try (ContextSnapshot.Scope scope = container.setThreadLocals()) {
            thenCurrentObservationHasParent(parent, child);

            // when second, nested scope created
            try (ContextSnapshot.Scope scope2 = container.setThreadLocals()) {
                thenCurrentObservationHasParent(parent, child);

                // when context gets propagated to a new thread
                ContextExecutorService.wrap(executorService, () -> container).submit(() -> {
                    thenCurrentObservationHasParent(parent, child);
                }).get(5, TimeUnit.SECONDS);

                thenCurrentObservationHasParent(parent, child);
            }
            thenCurrentObservationHasParent(parent, child);
        }

        thenCurrentObservationIsNull();

        child.stop();
        parent.stop();
    }

    private void thenCurrentObservationHasParent(Observation parent, Observation observation) {
        then(observationRegistry.getCurrentObservation()).isSameAs(observation);
        then(observationRegistry.getCurrentObservation().getContextView().getParentObservation()).isSameAs(parent);
    }

    private void thenCurrentObservationIsNull() {
        then(observationRegistry.getCurrentObservation()).isNull();
    }

}
