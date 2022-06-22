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

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

class ObservationThreadLocalAccessorTests {

    ObservationRegistry observationRegistry = ObservationRegistry.create();

    ContextSnapshot.Builder snapshotBuilder;

    @BeforeEach
    void setup() {
        observationRegistry.observationConfig().observationHandler(context -> true);
        ContextRegistry registry = new ContextRegistry();
        registry.registerThreadLocalAccessor(new ObservationThreadLocalAccessor());
        this.snapshotBuilder = ContextSnapshot.builder(registry);
    }

    @Test
    void capturedThreadLocalValuesShouldBeCapturedRestoredAndCleared() {
        Observation observation = Observation.start("foo", observationRegistry);
        then(observationRegistry.getCurrentObservation()).isNull();

        ContextSnapshot container = null;
        try (Observation.Scope scope = observation.openScope()) {
            then(observationRegistry.getCurrentObservation()).isSameAs(observation);
            container = snapshotBuilder.build();
        }

        then(observationRegistry.getCurrentObservation()).isNull();

        // when restored
        try (ContextSnapshot.Scope scope = container.setThreadLocalValues()) {
            then(observationRegistry.getCurrentObservation()).isSameAs(observation);
        }

        // then cleared
        then(observationRegistry.getCurrentObservation()).isNull();
    }

}
