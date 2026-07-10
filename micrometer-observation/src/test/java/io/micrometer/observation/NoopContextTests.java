/*
 * Copyright 2026 VMware, Inc.
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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NoopContext}.
 */
class NoopContextTests {

    @Test
    void observationFromNoopRegistryShouldReturnNoopContext() {
        assertThat(Observation.start("test", ObservationRegistry.NOOP).getContext()).isNotNull()
            .isSameAs(NoopContext.INSTANCE);
    }

    @Test
    void observationWithoutHandlerShouldReturnNoopContext() {
        ObservationRegistry registry = ObservationRegistry.create();
        assertThat(Observation.start("test", registry).getContext()).isNotNull().isSameAs(NoopContext.INSTANCE);
    }

    @Test
    void ignoredObservationShouldReturnNoopContext() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationPredicate((name, context) -> false);
        assertThat(Observation.start("test", registry).getContext()).isNotNull().isSameAs(NoopContext.INSTANCE);
    }

    @Test
    void observationShouldNotReturnNoopContext() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        assertThat(Observation.start("test", registry).getContext()).isNotNull().isNotSameAs(NoopContext.INSTANCE);
    }

    @Test
    void mutatorsShouldNotMutateContext() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        Observation.Context context = NoopContext.INSTANCE;

        context.setName("name");
        assertThat(context.getName()).isNull();

        context.setContextualName("contextualName");
        assertThat(context.getContextualName()).isNull();

        context.setError(new IllegalStateException("simulated"));
        assertThat(context.getError()).isNull();

        context.setParentObservation(Observation.start("parent", registry));
        assertThat(context.getParentObservation()).isNull();

        Observation parent = Observation.start("parent", registry);
        try (Observation.Scope ignored = parent.openScope()) {
            context.setParentFromCurrentObservation(registry);
            assertThat(registry.getCurrentObservation()).isNotNull().isSameAs(parent);
            assertThat(context.getParentObservation()).isNull();
        }

        context.addLowCardinalityKeyValue(KeyValue.of("lckv1", "lckv1"));
        context.addLowCardinalityKeyValues(KeyValues.of("lckv2", "lckv2"));
        assertThat(context.getLowCardinalityKeyValues()).isEmpty();

        context.addHighCardinalityKeyValue(KeyValue.of("hckv1", "hckv1"));
        context.addHighCardinalityKeyValues(KeyValues.of("hckv2", "hckv2"));
        assertThat(context.getHighCardinalityKeyValues()).isEmpty();

        context.put("testKey1", "testValue1");
        assertThat((String) context.get("testKey1")).isNull();

        context.computeIfAbsent("testKey2", key -> "testValue2");
        assertThat((String) context.get("testKey2")).isNull();

        // remove and clear methods are not tested
    }

}
