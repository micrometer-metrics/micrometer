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
package io.micrometer.api.instrument.observation;

import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link MeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
class ObservationRegistryTest {
    private final ObservationRegistry registry = new SimpleMeterRegistry();
    
    @Test
    void openingScopeShouldSetSampleAsCurrent() {
        Observation sample = Observation.start("test.timer", registry);
        Observation.Scope scope = sample.openScope();

        assertThat(registry.getCurrentObservation()).containsSame(sample);

        scope.close();
        sample.stop();

        assertThat(registry.getCurrentObservation()).isEmpty();
    }

    @Test
    void timerRecordingHandlerShouldAddThePassedHandler() {
        ObservationHandler<?> handler1 = mock(ObservationHandler.class);
        ObservationHandler<?> handler2 = mock(ObservationHandler.class);

        registry.observationConfig().observationHandler(handler1);
        assertThat(registry.observationConfig().getObservationHandlers()).containsExactly(handler1);

        registry.observationConfig().observationHandler(handler2);
        assertThat(registry.observationConfig().getObservationHandlers()).containsExactlyInAnyOrder(handler1, handler2);
    }


    @Test
    void observationShouldBeNoOpWhenPredicateApplicable() {
        registry.observationConfig().observationPredicate((name, context) -> !name.equals("test.timer"));

        Observation sample = Observation.start("test.timer", registry);

        assertThat(sample).isSameAs(NoopObservation.INSTANCE);
    }
}
