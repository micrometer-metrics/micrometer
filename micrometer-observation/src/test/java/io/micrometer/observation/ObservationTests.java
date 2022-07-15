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
package io.micrometer.observation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Observation}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 */
class ObservationTests {

    @Test
    void notHavingAnyHandlersShouldResultInNoopObservation() {
        ObservationRegistry registry = ObservationRegistry.create();

        Observation observation = Observation.createNotStarted("foo", registry);

        assertThat(observation).isSameAs(Observation.NOOP);
    }

    @Test
    void notHavingARegistryShouldResultInNoopObservation() {
        Observation observation = Observation.createNotStarted("foo", null);

        assertThat(observation).isSameAs(Observation.NOOP);
    }

    @Test
    void notMatchingObservationPredicateShouldResultInNoopObservation() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationPredicate((s, context) -> false);

        Observation observation = Observation.createNotStarted("foo", registry);

        assertThat(observation).isSameAs(Observation.NOOP);
    }

    @Test
    void matchingPredicateAndHandlerShouldNotResultInNoopObservation() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationPredicate((s, context) -> true);

        Observation observation = Observation.createNotStarted("foo", registry);

        assertThat(observation).isNotSameAs(Observation.NOOP);
    }

    @Test
    void havingAnObservationFilterWillMutateTheContext() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        registry.observationConfig().observationFilter(context -> context.put("foo", "bar"));
        Observation.Context context = new Observation.Context();

        Observation.start("foo", context, registry).stop();

        assertThat((String) context.get("foo")).isEqualTo("bar");
    }

    @Test
    void settingParentObservationMakesAReferenceOnParentContext() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);

        Observation.Context parentContext = new Observation.Context();
        Observation parent = Observation.start("parent", parentContext, registry);

        Observation.Context childContext = new Observation.Context();
        Observation child = Observation.createNotStarted("child", childContext, registry).parentObservation(parent)
                .start();

        parent.stop();
        child.stop();

        assertThat(childContext.getParentObservation().getContext()).isSameAs(parentContext);
    }

}
