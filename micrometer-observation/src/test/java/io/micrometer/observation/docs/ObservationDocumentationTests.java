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
package io.micrometer.observation.docs;

import java.util.function.Supplier;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.ObservationConvention;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ObservationDocumentationTests {

    @Test
    void iseShouldBeThrownWhenDocumentedObservationHasNotOverriddenDefaultConvention() {
        ObservationRegistry registry = observationRegistry();

        thenThrownBy(() -> TestConventionObservation.NOT_OVERRIDDEN_METHODS.observation(null, null,
                Observation.Context::new, registry))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("You've decided to use convention based naming yet this observation");
    }

    @Test
    void npeShouldBeThrownWhenDocumentedObservationHasOverriddenDefaultConventionButDefaultConventionWasNotPassedToTheFactoryMethod() {
        ObservationRegistry registry = observationRegistry();

        thenThrownBy(
                () -> TestConventionObservation.OVERRIDDEN.observation(null, null, Observation.Context::new, registry))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("You have not provided a default convention in the Observation factory method");
    }

    @Test
    void iaeShouldBeThrownWhenDocumentedObservationHasOverriddenDefaultConventionButDefaultConventionIsNotOfProperType() {
        ObservationRegistry registry = observationRegistry();

        thenThrownBy(() -> TestConventionObservation.OVERRIDDEN.observation(null, new SecondObservationConvention(),
                Observation.Context::new, registry))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("but you have provided an incompatible one of type");
    }

    @Test
    void observationShouldBeCreatedWhenObservationConventionIsOfProperType() {
        ObservationRegistry registry = observationRegistry();
        Observation.Context context = new Observation.Context();

        TestConventionObservation.OVERRIDDEN
            .observation(null, new ThirdObservationConvention(), () -> context, registry)
            .start()
            .stop();

        then(context.getName()).isEqualTo("three");
        then(context.getContextualName()).isEqualTo("contextual");
        then(context.getLowCardinalityKeyValues()).isEqualTo(KeyValues.of("low key", "low value"));
        then(context.getHighCardinalityKeyValues()).isEqualTo(KeyValues.of("high key", "high value"));
    }

    @Test
    void contextualNameShouldBeOverridden() {
        ObservationRegistry registry = observationRegistry();
        Observation.Context context = new Observation.Context();

        TestConventionObservation.CONTEXTUAL_NAME
            .observation(null, new ContextualObservationConvention(), () -> context, registry)
            .start()
            .stop();

        then(context.getName()).isEqualTo("technical name");
        then(context.getContextualName()).isEqualTo("contextual name");
    }

    @Test
    void globalConventionShouldBePickedIfItIsMatching() {
        ObservationRegistry registry = observationRegistry();
        registry.observationConfig().observationConvention(new GlobalConvention());
        Observation.Context context = new Observation.Context();

        TestConventionObservation.CONTEXTUAL_NAME
            .observation(null, new FirstObservationConvention(), () -> context, registry)
            .start()
            .stop();

        then(context.getName()).isEqualTo("global name");
        then(context.getContextualName()).isEqualTo("global contextual name");
        assertThat(context.getLowCardinalityKeyValues()).containsOnly(KeyValue.of("global", "low cardinality"));
        assertThat(context.getHighCardinalityKeyValues()).containsOnly(KeyValue.of("global", "high cardinality"));
    }

    @Test
    void keyValuesShouldBeAlwaysAdded() {
        ObservationRegistry registry = observationRegistry();
        registry.observationConfig().observationConvention(new GlobalConvention());
        registry.observationConfig().observationFilter(new KeyValueAddingObservationFilter());
        Observation.Context context = new Observation.Context();

        TestConventionObservation.CONTEXTUAL_NAME
            .observation(null, new FirstObservationConvention(), () -> context, registry)
            .start()
            .stop();

        then(context.getName()).isEqualTo("global name");
        then(context.getContextualName()).isEqualTo("global contextual name");
        assertThat(context.getLowCardinalityKeyValues()).containsOnly(KeyValue.of("always added", "tag"),
                KeyValue.of("global", "low cardinality"));
        assertThat(context.getHighCardinalityKeyValues()).containsOnly(KeyValue.of("global", "high cardinality"));
    }

    @Test
    void createNotStartedShouldNotCreateContextWithNoopRegistry() {
        ObservationRegistry registry = ObservationRegistry.NOOP;

        @SuppressWarnings("unchecked")
        Supplier<Context> supplier = mock(Supplier.class);

        Observation observation = TestConventionObservation.CONTEXTUAL_NAME.observation(null,
                new FirstObservationConvention(), supplier, registry);
        assertThat(observation.isNoop()).isTrue();
        verifyNoInteractions(supplier);
    }

    private ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        return registry;
    }

    enum TestConventionObservation implements ObservationDocumentation {

        NOT_OVERRIDDEN_METHODS {

        },

        OVERRIDDEN {
            @Override
            public String getContextualName() {
                return "contextual";
            }

            @Override
            public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
                return FirstObservationConvention.class;
            }
        },

        CONTEXTUAL_NAME {

            @Override
            public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
                return FirstObservationConvention.class;
            }
        }

    }

    static class FirstObservationConvention implements ObservationConvention<Observation.Context> {

        @Override
        public String getName() {
            return "one";
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class SecondObservationConvention implements ObservationConvention<Observation.Context> {

        @Override
        public String getName() {
            return "two";
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class ThirdObservationConvention extends FirstObservationConvention {

        @Override
        public String getName() {
            return "three";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("low key", "low value");
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("high key", "high value");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class ContextualObservationConvention extends FirstObservationConvention {

        @Override
        public String getName() {
            return "technical name";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "contextual name";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("low key", "low value");
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("high key", "high value");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class GlobalConvention implements GlobalObservationConvention<Observation.Context> {

        @Override
        public KeyValues getLowCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("global", "low cardinality");
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(Observation.Context context) {
            return KeyValues.of("global", "high cardinality");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public String getName() {
            return "global name";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "global contextual name";
        }

    }

    static class KeyValueAddingObservationFilter implements ObservationFilter {

        @Override
        public Observation.Context map(Observation.Context context) {
            return context.addLowCardinalityKeyValue(KeyValue.of("always added", "tag"));
        }

    }

}
