/*
 * Copyright 2017 VMware, Inc.
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
import java.time.Duration;

import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.observation.Observation;
import io.micrometer.api.instrument.observation.ObservationHandler;
import io.micrometer.api.instrument.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Base class for {@link MeterRegistry} compatibility tests.
 * To run a {@link MeterRegistry} implementation against this TCK, make a test class that extends this
 * and implement the abstract methods.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public abstract class ObservationRegistryCompatibilityKit {

    private ObservationRegistry registry;

    public abstract ObservationRegistry registry();
    public abstract Duration step();

    @BeforeEach
    void setup() {
        // assigned here rather than at initialization so subclasses can use fields in their registry() implementation
        registry = registry();
    }

    @Test
    @DisplayName("record using handlers")
    void recordWithHandlers() {
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handler = mock(ObservationHandler.class);
        @SuppressWarnings("unchecked")
        ObservationHandler<Observation.Context> handlerThatHandlesNothing = mock(ObservationHandler.class);
        registry.config().observationHandler(handler);
        registry.config().observationHandler(handlerThatHandlesNothing);
        when(handler.supportsContext(any())).thenReturn(true);
        when(handlerThatHandlesNothing.supportsContext(any())).thenReturn(false);

        Observation observation = registry.start("myObservation");
        verify(handler).supportsContext(isA(Observation.Context.class));
        verify(handler).onStart(isA(Observation.Context.class));
        verify(handlerThatHandlesNothing).supportsContext(isA(Observation.Context.class));
        verifyNoMoreInteractions(handlerThatHandlesNothing);

        try (Observation.Scope scope = observation.openScope()) {
            verify(handler).onScopeOpened(isA(Observation.Context.class));
            assertThat(scope.getCurrentObservation()).isSameAs(observation);

            Throwable exception = new IOException("simulated");
            observation.error(exception);
            verify(handler).onError(isA(Observation.Context.class));
        }
        verify(handler).onScopeClosed(isA(Observation.Context.class));
        observation.stop();
    }
}

