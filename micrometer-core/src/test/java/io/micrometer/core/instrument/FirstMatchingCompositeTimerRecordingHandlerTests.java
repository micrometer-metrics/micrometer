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
package io.micrometer.core.instrument;

import java.time.Duration;

import io.micrometer.core.instrument.TimerRecordingHandler.FirstMatchingCompositeTimerRecordingHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirstMatchingCompositeTimerRecordingHandlerTests {

    MatchingListener matchingListener = new MatchingListener();

    Timer.Sample sample = Timer.start(new SimpleMeterRegistry());

    @Test
    void should_run_on_start_only_for_first_matching_listener() {
        FirstMatchingCompositeTimerRecordingHandler FirstMatchingCompositeTimerRecordingHandler = new FirstMatchingCompositeTimerRecordingHandler(
                new NotMatchingListener(), this.matchingListener, new NotMatchingListener());

        FirstMatchingCompositeTimerRecordingHandler.onStart(sample, null);

        assertThat(this.matchingListener.started).isTrue();
    }

    @Test
    void should_run_on_stop_only_for_first_matching_listener() {
        FirstMatchingCompositeTimerRecordingHandler FirstMatchingCompositeTimerRecordingHandler = new FirstMatchingCompositeTimerRecordingHandler(
                new NotMatchingListener(), this.matchingListener, new NotMatchingListener());

        FirstMatchingCompositeTimerRecordingHandler.onStop(sample, null, null, null);

        assertThat(this.matchingListener.stopped).isTrue();
    }

    @Test
    void should_run_on_error_only_for_first_matching_listener() {
        FirstMatchingCompositeTimerRecordingHandler FirstMatchingCompositeTimerRecordingHandler = new FirstMatchingCompositeTimerRecordingHandler(
                new NotMatchingListener(), this.matchingListener, new NotMatchingListener());

        FirstMatchingCompositeTimerRecordingHandler.onError(sample, null, new RuntimeException());

        assertThat(this.matchingListener.errored).isTrue();
    }

    @Test
    void should_run_on_restore_only_for_first_matching_listener() {
        FirstMatchingCompositeTimerRecordingHandler FirstMatchingCompositeTimerRecordingHandler = new FirstMatchingCompositeTimerRecordingHandler(
                new NotMatchingListener(), this.matchingListener, new NotMatchingListener());

        FirstMatchingCompositeTimerRecordingHandler.onScopeOpened(sample, null);

        assertThat(this.matchingListener.restored).isTrue();
    }

    static class MatchingListener implements TimerRecordingHandler {

        boolean started;

        boolean stopped;

        boolean errored;

        boolean restored;


        @Override
        public void onStart(Timer.Sample sample, Timer.HandlerContext context) {
            this.started = true;
        }

        @Override
        public void onError(Timer.Sample sample, Timer.HandlerContext context, Throwable throwable) {
            this.errored = true;
        }

        @Override
        public void onScopeOpened(Timer.Sample sample, Timer.HandlerContext context) {
            this.restored = true;
        }

        @Override
        public void onStop(Timer.Sample sample, Timer.HandlerContext context, Timer timer, Duration duration) {
            this.stopped = true;
        }

        @Override
        public boolean supportsContext(Timer.HandlerContext handlerContext) {
            return true;
        }
    }

    static class NotMatchingListener implements TimerRecordingHandler {

        @Override
        public void onStart(Timer.Sample sample, Timer.HandlerContext context) {
            throwAssertionError();
        }

        @Override
        public void onError(Timer.Sample sample, Timer.HandlerContext context, Throwable throwable) {
            throwAssertionError();
        }

        @Override
        public void onScopeOpened(Timer.Sample sample, Timer.HandlerContext context) {
            throwAssertionError();
        }

        @Override
        public void onStop(Timer.Sample sample, Timer.HandlerContext context, Timer timer, Duration duration) {
            throwAssertionError();
        }

        @Override
        public boolean supportsContext(Timer.HandlerContext handlerContext) {
            return false;
        }

        private void throwAssertionError() {
            throw new AssertionError("Not matching listener must not be called");
        }

    }

}
