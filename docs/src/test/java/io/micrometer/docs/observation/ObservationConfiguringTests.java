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

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Sources for observation-components.adoc
 */
class ObservationConfiguringTests {

    void yourCodeToMeasure() {

    }

    @Test
    void observation_config_customization() {
        // tag::predicate_and_filter[]
        // Example using a metrics handler - we need a MeterRegistry
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        // Create an ObservationRegistry
        ObservationRegistry registry = ObservationRegistry.create();
        // Add predicates and filter to the registry
        registry.observationConfig()
            // ObservationPredicate can decide whether an observation should be
            // ignored or not
            .observationPredicate((observationName, context) -> {
                // Creates a noop observation if observation name is of given name
                if ("to.ignore".equals(observationName)) {
                    // Will be ignored
                    return false;
                }
                if (context instanceof MyContext) {
                    // For the custom context will ignore a user with a given name
                    return !"user to ignore".equals(((MyContext) context).getUsername());
                }
                // Will proceed for all other types of context
                return true;
            })
            // ObservationFilter can modify a context
            .observationFilter(context -> {
                // We're adding a low cardinality key to all contexts
                context.addLowCardinalityKeyValue(KeyValue.of("low.cardinality.key", "low cardinality value"));
                if (context instanceof MyContext) {
                    // We're mutating a specific type of a context
                    MyContext myContext = (MyContext) context;
                    myContext.setUsername("some username");
                    // We want to remove a high cardinality key value
                    return myContext.removeHighCardinalityKeyValue("high.cardinality.key.to.ignore");
                }
                return context;
            })
            // Example of using metrics
            .observationHandler(new DefaultMeterObservationHandler(meterRegistry));

        // Observation will be ignored because of the name
        then(Observation.start("to.ignore", () -> new MyContext("don't ignore"), registry)).isSameAs(Observation.NOOP);
        // Observation will be ignored because of the entries in MyContext
        then(Observation.start("not.to.ignore", () -> new MyContext("user to ignore"), registry))
            .isSameAs(Observation.NOOP);

        // Observation will not be ignored...
        MyContext myContext = new MyContext("user not to ignore");
        myContext.addHighCardinalityKeyValue(KeyValue.of("high.cardinality.key.to.ignore", "some value"));
        Observation.createNotStarted("not.to.ignore", () -> myContext, registry).observe(this::yourCodeToMeasure);
        // ...and will have the context mutated
        then(myContext.getLowCardinalityKeyValue("low.cardinality.key").getValue()).isEqualTo("low cardinality value");
        then(myContext.getUsername()).isEqualTo("some username");
        then(myContext.getHighCardinalityKeyValues())
            .doesNotContain(KeyValue.of("high.cardinality.key.to.ignore", "some value"));
        // end::predicate_and_filter[]
    }

    static class MyContext extends Observation.Context {

        String username;

        MyContext(String username) {
            this.username = username;
        }

        String getUsername() {
            return username;
        }

        void setUsername(String username) {
            this.username = username;
        }

    }

}
