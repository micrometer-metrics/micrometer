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
package io.micrometer.observation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ObservationRegistry}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 */
class ObservationRegistryTest {

    private final ObservationRegistry registry = ObservationRegistry.create();

    @Test
    void openingScopeShouldSetSampleAsCurrent() {
        registry.observationConfig().observationHandler(c -> true);
        Observation sample = Observation.start("test.timer", registry);
        Observation.Scope scope = sample.openScope();

        assertThat(registry.getCurrentObservation()).isSameAs(sample);

        scope.close();
        sample.stop();

        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void observationHandlersShouldBeAddedToTheRegistry() {
        ObservationHandler<?> handler1 = mock(ObservationHandler.class);
        ObservationHandler<?> handler2 = mock(ObservationHandler.class);

        registry.observationConfig().observationHandler(handler1);
        assertThat(registry.observationConfig().getObservationHandlers()).containsExactly(handler1);

        registry.observationConfig().observationHandler(handler2);
        assertThat(registry.observationConfig().getObservationHandlers()).containsExactlyInAnyOrder(handler1, handler2);
    }

    @Test
    void observationShouldBeNoopWhenPredicateApplicable() {
        registry.observationConfig().observationPredicate((name, context) -> !name.equals("test.timer"));

        Observation sample = Observation.start("test.timer", registry);

        assertThat(sample).isSameAs(NoopObservation.INSTANCE);
    }

    @Test
    void observationShouldBeNoopWhenNullRegistry() {
        assertThat(Observation.start("test.timer", null)).isSameAs(NoopObservation.INSTANCE);
        assertThat(Observation.start("test.timer", new Observation.Context(), null)).isSameAs(NoopObservation.INSTANCE);
        assertThat(Observation.createNotStarted("test.timer", null)).isSameAs(NoopObservation.INSTANCE);
        assertThat(Observation.createNotStarted("test.timer", new Observation.Context(), null))
                .isSameAs(NoopObservation.INSTANCE);
    }

    @Test
    void observationShouldNotBeNoopWhenNonNullRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(c -> true);
        assertThat(Observation.start("test.timer", registry)).isInstanceOf(SimpleObservation.class);
        assertThat(Observation.start("test.timer", new Observation.Context(), registry))
                .isInstanceOf(SimpleObservation.class);
        assertThat(Observation.createNotStarted("test.timer", registry)).isInstanceOf(SimpleObservation.class);
        assertThat(Observation.createNotStarted("test.timer", new Observation.Context(), registry))
                .isInstanceOf(SimpleObservation.class);
    }

}
