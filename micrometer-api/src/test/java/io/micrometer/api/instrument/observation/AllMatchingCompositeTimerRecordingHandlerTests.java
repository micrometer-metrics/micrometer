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

import io.micrometer.api.instrument.observation.ObservationHandler.AllMatchingCompositeObservationHandler;
import io.micrometer.api.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AllMatchingCompositeTimerRecordingHandlerTests {

    MatchingHandler matchingHandler = new MatchingHandler();

    MatchingHandler matchingHandler2 = new MatchingHandler();

    ObservationRegistry registry = new SimpleMeterRegistry();

    Observation sample = Observation.start("hello", registry);

    @Test
    void should_run_on_start_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingCompositeTimerRecordingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingCompositeTimerRecordingHandler.onStart(null);

        assertThat(this.matchingHandler.started).isTrue();
        assertThat(this.matchingHandler2.started).isTrue();
    }

    @Test
    void should_run_on_stop_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingCompositeTimerRecordingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingCompositeTimerRecordingHandler.onStop(null);

        assertThat(this.matchingHandler.stopped).isTrue();
        assertThat(this.matchingHandler2.stopped).isTrue();
    }

    @Test
    void should_run_on_error_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingCompositeTimerRecordingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingCompositeTimerRecordingHandler.onError(null);

        assertThat(this.matchingHandler.errored).isTrue();
        assertThat(this.matchingHandler2.errored).isTrue();
    }

    @Test
    void should_run_on_scope_opened_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingCompositeTimerRecordingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingCompositeTimerRecordingHandler.onScopeOpened(null);

        assertThat(this.matchingHandler.scopeOpened).isTrue();
        assertThat(this.matchingHandler2.scopeOpened).isTrue();
    }

    @Test
    void should_run_on_scope_closed_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingCompositeTimerRecordingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingCompositeTimerRecordingHandler.onScopeClosed(null);

        assertThat(this.matchingHandler.scopeClosed).isTrue();
        assertThat(this.matchingHandler2.scopeClosed).isTrue();
    }

    static class MatchingHandler implements ObservationHandler<Observation.Context> {

        boolean started;

        boolean stopped;

        boolean errored;

        boolean scopeOpened;

        boolean scopeClosed;


        @Override
        public void onStart(Observation.Context context) {
            this.started = true;
        }

        @Override
        public void onError(Observation.Context context) {
            this.errored = true;
        }

        @Override
        public void onScopeOpened(Observation.Context context) {
            this.scopeOpened = true;
        }

        @Override
        public void onScopeClosed(Observation.Context context) {
            this.scopeClosed = true;
        }

        @Override
        public void onStop(Observation.Context context) {
            this.stopped = true;
        }

        @Override
        public boolean supportsContext(Observation.Context handlerContext) {
            return true;
        }
    }

    static class NotMatchingHandler implements ObservationHandler<Observation.Context> {

        @Override
        public void onStart(Observation.Context context) {
            throwAssertionError();
        }

        @Override
        public void onError(Observation.Context context) {
            throwAssertionError();
        }

        @Override
        public void onScopeOpened(Observation.Context context) {
            throwAssertionError();
        }

        @Override
        public void onStop(Observation.Context context) {
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
