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
package io.micrometer.observation.tck;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Base class for {@link ObservationHandler} compatibility tests that support any context.
 * To run an {@link ObservationHandler} implementation against this TCK, make a test class
 * that extends this and implement the abstract methods.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public abstract class AnyContextObservationHandlerCompatibilityKit
        extends NullContextObservationHandlerCompatibilityKit {

    @Test
    @DisplayName("compatibility test provides a test context accepting observation handler")
    void handlerSupportsAnyContext() {
        TestContext testContext = new TestContext();
        assertThatCode(() -> handler.onStart(testContext)).doesNotThrowAnyException();
        assertThatCode(() -> handler.onStop(testContext)).doesNotThrowAnyException();
        assertThatCode(() -> handler.onError(testContext)).doesNotThrowAnyException();
        assertThatCode(() -> handler.onEvent(Observation.Event.of("testEvent"), testContext))
            .doesNotThrowAnyException();
        assertThatCode(() -> handler.onScopeOpened(testContext)).doesNotThrowAnyException();
        assertThatCode(() -> handler.onScopeClosed(testContext)).doesNotThrowAnyException();
        assertThatCode(() -> handler.onScopeReset(testContext)).doesNotThrowAnyException();
        assertThatCode(() -> handler.supportsContext(testContext)).doesNotThrowAnyException();
        assertThat(handler.supportsContext(testContext)).as("Handler supports any context").isTrue();
    }

    static class TestContext extends Observation.Context {

    }

}
