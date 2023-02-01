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
package io.micrometer.observation;

import io.micrometer.observation.ObservationHandler.AllMatchingCompositeObservationHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllMatchingCompositeObservationHandlerTests {

    MatchingHandler matchingHandler = new MatchingHandler();

    MatchingHandler matchingHandler2 = new MatchingHandler();

    @Test
    void should_run_on_start_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onStart(null);

        assertThat(this.matchingHandler.started).isTrue();
        assertThat(this.matchingHandler2.started).isTrue();
    }

    @Test
    void should_run_on_stop_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onStop(null);

        assertThat(this.matchingHandler.stopped).isTrue();
        assertThat(this.matchingHandler2.stopped).isTrue();
    }

    @Test
    void should_run_on_error_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onError(null);

        assertThat(this.matchingHandler.errored).isTrue();
        assertThat(this.matchingHandler2.errored).isTrue();
    }

    @Test
    void should_run_on_event_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onEvent(Observation.Event.of("testEvent"), null);

        assertThat(this.matchingHandler.eventDetected).isTrue();
        assertThat(this.matchingHandler2.eventDetected).isTrue();
    }

    @Test
    void should_run_on_scope_opened_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onScopeOpened(null);

        assertThat(this.matchingHandler.scopeOpened).isTrue();
        assertThat(this.matchingHandler2.scopeOpened).isTrue();
    }

    @Test
    void should_run_on_scope_closed_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onScopeClosed(null);

        assertThat(this.matchingHandler.scopeClosed).isTrue();
        assertThat(this.matchingHandler2.scopeClosed).isTrue();
    }

    @Test
    void should_run_on_scope_reset_for_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                this.matchingHandler, this.matchingHandler2);

        allMatchingHandler.onScopeReset(null);

        assertThat(this.matchingHandler.scopeReset).isTrue();
        assertThat(this.matchingHandler2.scopeReset).isTrue();
    }

    @Test
    void should_support_the_context_if_any_handler_supports_it() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);
        assertThat(allMatchingHandler.supportsContext(new Observation.Context())).isTrue();
    }

    @Test
    void should_return_observation_handlers() {
        List<ObservationHandler<? extends Observation.Context>> handlers = new ArrayList<>();
        handlers.add(new NotMatchingHandler());
        handlers.add(this.matchingHandler);
        handlers.add(new NotMatchingHandler());
        handlers.add(this.matchingHandler2);
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                handlers);
        assertThat(allMatchingHandler.getHandlers()).isEqualTo(handlers);
    }

    @Test
    void should_return_custom_handlers() {
        List<NotMatchingHandler> handlers = new ArrayList<>();
        handlers.add(new NotMatchingHandler());
        handlers.add(new NotMatchingHandler());
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                handlers);
        assertThat(allMatchingHandler.getHandlers()).isEqualTo(handlers);
    }

    static class MatchingHandler implements ObservationHandler<Observation.Context> {

        boolean started;

        boolean stopped;

        boolean errored;

        boolean eventDetected;

        boolean scopeOpened;

        boolean scopeClosed;

        boolean scopeReset;

        @Override
        public void onStart(Observation.Context context) {
            this.started = true;
        }

        @Override
        public void onError(Observation.Context context) {
            this.errored = true;
        }

        @Override
        public void onEvent(Observation.Event event, Observation.Context context) {
            this.eventDetected = true;
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
        public void onScopeReset(Observation.Context context) {
            this.scopeReset = true;
        }

        @Override
        public void onStop(Observation.Context context) {
            this.stopped = true;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

    }

    static class NotMatchingHandler implements ObservationHandler<CustomContext> {

        @Override
        public void onStart(CustomContext context) {
            throwAssertionError();
        }

        @Override
        public void onError(CustomContext context) {
            throwAssertionError();
        }

        @Override
        public void onScopeOpened(CustomContext context) {
            throwAssertionError();
        }

        @Override
        public void onStop(CustomContext context) {
            throwAssertionError();
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return false;
        }

        private void throwAssertionError() {
            throw new AssertionError("Not matching handler must not be called");
        }

    }

    static class CustomContext extends Observation.Context {

    }

}
