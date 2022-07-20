/*
 * Copyright 2021 VMware, Inc.
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

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class DocumentedObservationTests {

    @Test
    void iseShouldBeThrownWhenDocumentedObservationHasNotOverriddenDefaultConvention() {
        ObservationRegistry registry = observationRegistry();

        thenThrownBy(() -> TestConventionObservation.NOT_OVERRIDDEN_METHODS.observation(null, null,
                new Observation.Context(), registry)).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("You've decided to use convention based naming yet this observation");
    }

    @Test
    void npeShouldBeThrownWhenDocumentedObservationHasOverriddenDefaultConventionButDefaultConventionWasNotPassedToTheFactoryMethod() {
        ObservationRegistry registry = observationRegistry();

        thenThrownBy(
                () -> TestConventionObservation.OVERRIDDEN.observation(null, null, new Observation.Context(), registry))
                        .isInstanceOf(NullPointerException.class).hasMessageContaining(
                                "You have not provided a default convention in the Observation factory method");
    }

    @Test
    void iaeShouldBeThrownWhenDocumentedObservationHasOverriddenDefaultConventionButDefaultConventionIsNotOfProperType() {
        ObservationRegistry registry = observationRegistry();

        thenThrownBy(() -> TestConventionObservation.OVERRIDDEN.observation(null, new SecondObservationConvention(),
                new Observation.Context(), registry)).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("but you have provided an incompatible one of type");
    }

    @Test
    void observationShouldBeCreatedWhenObservationConventionIsOfProperType() {
        ObservationRegistry registry = observationRegistry();
        Observation.Context context = new Observation.Context();

        TestConventionObservation.OVERRIDDEN.observation(null, new ThirdObservationConvention(), context, registry)
                .start().stop();

        then(context.getName()).isEqualTo("three");
        then(context.getContextualName()).isEqualTo("contextual");
        then(context.getLowCardinalityKeyValues()).isEqualTo(KeyValues.of("low key", "low value"));
        then(context.getHighCardinalityKeyValues()).isEqualTo(KeyValues.of("high key", "high value"));
    }

    @Test
    void contextualNameShouldBeOverridden() {
        ObservationRegistry registry = observationRegistry();
        Observation.Context context = new Observation.Context();

        TestConventionObservation.CONTEXTUAL_NAME.observation(null, new ContextualObservation(), context, registry)
                .start().stop();

        then(context.getName()).isEqualTo("technical name");
        then(context.getContextualName()).isEqualTo("contextual name");
    }

    private ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        return registry;
    }

    enum TestConventionObservation implements DocumentedObservation {

        NOT_OVERRIDDEN_METHODS {

        },

        OVERRIDDEN {
            @Override
            public String getContextualName() {
                return "contextual";
            }

            @Override
            public Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
                return FirstObservationConvention.class;
            }
        },

        CONTEXTUAL_NAME {

            @Override
            public Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
                return FirstObservationConvention.class;
            }
        }

    }

    static class FirstObservationConvention implements Observation.ObservationConvention<Observation.Context> {

        @Override
        public String getName() {
            return "one";
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class SecondObservationConvention implements Observation.ObservationConvention<Observation.Context> {

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

    static class ContextualObservation extends FirstObservationConvention {

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

}
