/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.tck;

import java.time.Duration;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.TimerRecordingListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Base class for {@link TimerRecordingListener} compatibility tests that support any context.
 * To run a {@link TimerRecordingListener} implementation against this TCK, make a test class that extends this
 * and implement the abstract methods.
 *
 * @author Marcin Grzejszczak
 */
public abstract class AnyContextTimerRecordingListenerCompatibilityKit extends NullContextTimerRecordingListenerCompatibilityKit {

    @Test
    @DisplayName("compatibility test provides a test context accepting timer recording listener")
    void listenerSupportsAnyContext() {
        TestContext testContext = new TestContext();
        assertThatCode(() -> listener.onStart(sample, testContext)).doesNotThrowAnyException();
        assertThatCode(() -> listener.onStop(sample, testContext, Timer.builder("timer for test context")
                .register(meterRegistry), Duration.ofSeconds(1L))).doesNotThrowAnyException();
        assertThatCode(() -> listener.onError(sample, testContext, new RuntimeException())).doesNotThrowAnyException();
        assertThatCode(() -> listener.onRestore(sample, testContext)).doesNotThrowAnyException();
        assertThatCode(() -> listener.supportsContext(testContext)).doesNotThrowAnyException();
        assertThat(listener.supportsContext(testContext)).as("Listener supports any context").isTrue();
    }

    static class TestContext extends Timer.Context {

    }
}

