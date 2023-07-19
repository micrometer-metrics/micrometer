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

import io.micrometer.observation.ObservationHandler.FirstMatchingCompositeObservationHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FirstMatchingCompositeObservationHandlerTests {

    MatchingHandler matchingHandler = new MatchingHandler();

    @Test
    void should_run_on_start_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingHandler.onStart(null);

        assertThat(this.matchingHandler.started).isTrue();
    }

    @Test
    void should_run_on_stop_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingHandler.onStop(null);

        assertThat(this.matchingHandler.stopped).isTrue();
    }

    @Test
    void should_run_on_error_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingHandler.onError(null);

        assertThat(this.matchingHandler.errored).isTrue();
    }

    @Test
    void should_run_on_event_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingHandler.onEvent(Observation.Event.of("testEvent"), null);

        assertThat(this.matchingHandler.eventDetected).isTrue();
    }

    @Test
    void should_run_on_scope_opened_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingHandler.onScopeOpened(null);

        assertThat(this.matchingHandler.scopeOpened).isTrue();
    }

    @Test
    void should_run_on_scope_closed_only_for_first_matching_handler() {
        FirstMatchingCompositeObservationHandler firstMatchingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingHandler.onScopeClosed(null);

        assertThat(this.matchingHandler.scopeClosed).isTrue();
    }

    @Test
    void should_run_on_scope_reset_for_handlers() {
        FirstMatchingCompositeObservationHandler firstMatchingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());

        firstMatchingHandler.onScopeReset(null);

        assertThat(this.matchingHandler.scopeReset).isTrue();
    }

    @Test
    void should_support_the_context_if_any_handler_supports_it() {
        FirstMatchingCompositeObservationHandler firstMatchingHandler = new FirstMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler());
        assertThat(firstMatchingHandler.supportsContext(new Observation.Context())).isTrue();
    }

    @Test
    void should_return_observation_handlers() {
        List<ObservationHandler<? extends Observation.Context>> handlers = new ArrayList<>();
        handlers.add(new NotMatchingHandler());
        handlers.add(this.matchingHandler);
        handlers.add(new NotMatchingHandler());
        FirstMatchingCompositeObservationHandler firstMatchingHandler = new FirstMatchingCompositeObservationHandler(
                handlers);
        assertThat(firstMatchingHandler.getHandlers()).isEqualTo(handlers);
    }

    @Test
    void should_return_custom_handlers() {
        List<NotMatchingHandler> handlers = new ArrayList<>();
        handlers.add(new NotMatchingHandler());
        handlers.add(new NotMatchingHandler());
        FirstMatchingCompositeObservationHandler firstMatchingHandler = new FirstMatchingCompositeObservationHandler(
                handlers);
        assertThat(firstMatchingHandler.getHandlers()).isEqualTo(handlers);
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
