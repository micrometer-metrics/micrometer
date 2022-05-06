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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class AllMatchingCompositeObservationHandlerTests {

    MatchingHandler matchingHandler = new MatchingHandler();

    MatchingHandler matchingHandler2 = new MatchingHandler();

    @Test
    void should_run_on_start_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onStart(null);

        Assertions.assertThat(this.matchingHandler.started).isTrue();
        Assertions.assertThat(this.matchingHandler2.started).isTrue();
    }

    @Test
    void should_run_on_stop_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onStop(null);

        Assertions.assertThat(this.matchingHandler.stopped).isTrue();
        Assertions.assertThat(this.matchingHandler2.stopped).isTrue();
    }

    @Test
    void should_run_on_error_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onError(null);

        Assertions.assertThat(this.matchingHandler.errored).isTrue();
        Assertions.assertThat(this.matchingHandler2.errored).isTrue();
    }

    @Test
    void should_run_on_scope_opened_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onScopeOpened(null);

        Assertions.assertThat(this.matchingHandler.scopeOpened).isTrue();
        Assertions.assertThat(this.matchingHandler2.scopeOpened).isTrue();
    }

    @Test
    void should_run_on_scope_closed_for_all_matching_handlers() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);

        allMatchingHandler.onScopeClosed(null);

        Assertions.assertThat(this.matchingHandler.scopeClosed).isTrue();
        Assertions.assertThat(this.matchingHandler2.scopeClosed).isTrue();
    }

    @Test
    void should_support_the_context_if_any_handler_supports_it() {
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                new NotMatchingHandler(), this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);
        Assertions.assertThat(allMatchingHandler.supportsContext(new Observation.Context())).isTrue();
    }

    @Test
    void should_return_handlers() {
        List<ObservationHandler<Observation.Context>> handlers = Arrays.asList(new NotMatchingHandler(),
                this.matchingHandler, new NotMatchingHandler(), this.matchingHandler2);
        AllMatchingCompositeObservationHandler allMatchingHandler = new AllMatchingCompositeObservationHandler(
                handlers);
        Assertions.assertThat(allMatchingHandler.getHandlers()).isSameAs(handlers);
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
        public boolean supportsContext(Observation.Context context) {
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
        public boolean supportsContext(Observation.Context context) {
            return false;
        }

        private void throwAssertionError() {
            throw new AssertionError("Not matching handler must not be called");
        }

    }

}
