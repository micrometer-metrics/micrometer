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
package io.micrometer.api.instrument.observation;

import io.micrometer.api.instrument.observation.ObservationHandler.FirstMatchingCompositeObservationHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirstMatchingCompositeObservationHandlerTests {

    MatchingHandler matchingHandler = new MatchingHandler();

    ObservationRegistry registry = new SimpleObservationRegistry();

    Observation sample = registry.start("hello");

    @Test
    void should_run_on_start_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingCompositeTimerRecordingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingCompositeTimerRecordingHandler.onStart(sample, null);

        assertThat(this.matchingHandler.started).isTrue();
    }

    @Test
    void should_run_on_stop_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingCompositeTimerRecordingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingCompositeTimerRecordingHandler.onStop(sample, null);

        assertThat(this.matchingHandler.stopped).isTrue();
    }

    @Test
    void should_run_on_error_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingCompositeTimerRecordingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingCompositeTimerRecordingHandler.onError(sample, null, new RuntimeException());

        assertThat(this.matchingHandler.errored).isTrue();
    }

    @Test
    void should_run_on_scope_opened_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingCompositeTimerRecordingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingCompositeTimerRecordingHandler.onScopeOpened(sample, null);

        assertThat(this.matchingHandler.scopeOpened).isTrue();
    }

    @Test
    void should_run_on_scope_closed_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingCompositeTimerRecordingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingCompositeTimerRecordingHandler.onScopeClosed(sample, null);

        assertThat(this.matchingHandler.scopeClosed).isTrue();
    }

    static class MatchingHandler implements ObservationHandler {

        boolean started;

        boolean stopped;

        boolean errored;

        boolean scopeOpened;

        boolean scopeClosed;


        @Override
        public void onStart(Observation observation, Observation.Context context) {
            this.started = true;
        }

        @Override
        public void onError(Observation observation, Observation.Context context, Throwable throwable) {
            this.errored = true;
        }

        @Override
        public void onScopeOpened(Observation observation, Observation.Context context) {
            this.scopeOpened = true;
        }

        @Override
        public void onScopeClosed(Observation observation, Observation.Context context) {
            this.scopeClosed = true;
        }

        @Override
        public void onStop(Observation observation, Observation.Context context) {
            this.stopped = true;
        }

        @Override
        public boolean supportsContext(Observation.Context handlerContext) {
            return true;
        }
    }

    static class NotMatchingHandler implements ObservationHandler {

        @Override
        public void onStart(Observation observation, Observation.Context context) {
            throwAssertionError();
        }

        @Override
        public void onError(Observation observation, Observation.Context context, Throwable throwable) {
            throwAssertionError();
        }

        @Override
        public void onScopeOpened(Observation observation, Observation.Context context) {
            throwAssertionError();
        }

        @Override
        public void onStop(Observation observation, Observation.Context context) {
            throwAssertionError();
        }

        @Override
        public boolean supportsContext(Observation.Context handlerContext) {
            return false;
        }

        private void throwAssertionError() {
            throw new AssertionError("Not matching handler must not be called");
        }

    }

}
