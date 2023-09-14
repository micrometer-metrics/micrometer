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
package io.micrometer.docs.context;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshot.Scope;
import io.micrometer.context.ContextSnapshotFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Source for contextpropagation/index.adoc
 */
class DefaultContextSnapshotTests {

    @Test
    void should_propagate_thread_local() {
        // tag::simple[]
        // Create a new Context Registry (you can use a global too)
        ContextRegistry registry = new ContextRegistry();
        // Register thread local accessors (you can use SPI too)
        registry.registerThreadLocalAccessor(new ObservationThreadLocalAccessor());

        // When you set a thread local value...
        ObservationThreadLocalHolder.setValue("hello");
        // ... we can capture it using ContextSnapshot
        ContextSnapshot snapshot = ContextSnapshotFactory.builder().contextRegistry(registry).build().captureAll();

        // After capturing if you change the thread local value again ContextSnapshot will
        // not see it
        ObservationThreadLocalHolder.setValue("hola");
        try {
            // We're populating the thread local values with what we had in
            // ContextSnapshot
            try (Scope scope = snapshot.setThreadLocals()) {
                // Within this scope you will see the stored thread local values
                then(ObservationThreadLocalHolder.getValue()).isEqualTo("hello");
            }
            // After the scope is closed we will come back to the previously present
            // values in thread local
            then(ObservationThreadLocalHolder.getValue()).isEqualTo("hola");
        }
        finally {
            // We're clearing the thread local values so that we don't pollute the thread
            ObservationThreadLocalHolder.reset();
        }
        // end::simple[]
    }

}
