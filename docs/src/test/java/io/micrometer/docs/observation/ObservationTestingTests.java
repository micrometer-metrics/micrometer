/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.docs.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sources for observation-testing.adoc
 */
class ObservationTestingTests {

    // @formatter:off
    // tag::test[]
    @Test
    void should_assert_your_observation() {
        // create a test registry in your tests
        TestObservationRegistry registry = TestObservationRegistry.create();

        // run your production code with the TestObservationRegistry
        new Example(registry).run();

        // check your observation
        assertThat(registry)
                .doesNotHaveAnyRemainingCurrentObservation()
                .hasObservationWithNameEqualTo("foo")
                .that()
                .hasHighCardinalityKeyValue("highTag", "highTagValue")
                .hasLowCardinalityKeyValue("lowTag", "lowTagValue")
                .hasEvent("event1")
                .hasBeenStarted()
                .hasBeenStopped();
    }
    // end::test[]
    // @formatter:on

    class SomeComponent {

        private final ObservationRegistry registry;

        public SomeComponent(ObservationRegistry registry) {
            this.registry = registry;
        }

        void doSthThatShouldCreateSpans() {
            try {
                Observation.createNotStarted("insert user", () -> new CustomContext("mongodb-database"), this.registry)
                    .highCardinalityKeyValue("mongodb.command", "insert")
                    .highCardinalityKeyValue("mongodb.collection", "user")
                    .highCardinalityKeyValue("mongodb.cluster_id", "some_id")
                    .observe(() -> {
                        System.out.println("hello");
                        throw new IllegalStateException("Boom!");
                    });
            }
            catch (Exception ex) {

            }
        }

    }

    // @formatter:off
    // tag::example[]
    static class Example {

        private final ObservationRegistry registry;

        Example(ObservationRegistry registry) {
            this.registry = registry;
        }

        void run() {
            Observation observation = Observation.createNotStarted("foo", registry)
                    .lowCardinalityKeyValue("lowTag", "lowTagValue")
                    .highCardinalityKeyValue("highTag", "highTagValue");
            observation.observe(() -> {
                observation.event(Observation.Event.of("event1"));
                System.out.println("Hello");
            });
        }

    }
    // end::example[]
    // @formatter:on

    static class CustomContext extends Observation.Context {

        private final String databaseName;

        CustomContext(String databaseName) {
            this.databaseName = databaseName;
        }

        String getDatabaseName() {
            return databaseName;
        }

    }

}
