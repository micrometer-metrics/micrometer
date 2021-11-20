/**
 * Copyright 2021 VMware, Inc.
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.micrometer.core.lang.Nullable;

/**
 * Handler with callbacks for the {@link Timer#start(MeterRegistry) start} and
 * {@link io.micrometer.core.instrument.Timer.Sample#stop(Timer) stop} of a {@link Timer} recording.
 *
 * @since 2.0.0
 */
public interface TimerRecordingHandler<T extends Timer.HandlerContext> {
    /**
     * @param sample the sample that was started
     * @param context handler context
     */
    void onStart(Timer.Sample sample, @Nullable T context);

    /**
     * @param sample sample for which the error happened
     * @param context handler context
     * @param throwable exception that happened during recording
     */
    void onError(Timer.Sample sample, @Nullable T context, Throwable throwable);
    
    /**
     * @param sample sample for which the scope was opened
     * @param context handler context
     */
    default void onScopeOpened(Timer.Sample sample, @Nullable T context) {
    }

    /**
     * @param sample sample for which the scope was closed
     * @param context handler context
     */
    default void onScopeClosed(Timer.Sample sample, @Nullable T context) {
    }

    /**
     * @param sample the sample that was stopped
     * @param context handler context
     * @param timer the timer to which the recording was made
     * @param duration time recorded
     */
    void onStop(Timer.Sample sample, @Nullable T context, Timer timer, Duration duration);

    /**
     * @param handlerContext handler context, may be {@code null}
     * @return {@code true} when this handler context is supported
     */
    boolean supportsContext(@Nullable Timer.HandlerContext handlerContext);

    /**
     * Handler wrapping other handlers.
     *
     * @since 2.0.0
     */
    interface CompositeTimerRecordingHandler extends TimerRecordingHandler<Timer.HandlerContext> {
        /**
         * Returns the registered recording handlers.
         * @return registered handlers
         */
        List<TimerRecordingHandler> getHandlers();
    }

    /**
     * Handler picking the first matching recording handler from the list.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    class FirstMatchingCompositeTimerRecordingHandler implements CompositeTimerRecordingHandler {

        private final List<TimerRecordingHandler> handlers;

        /**
         * Creates a new instance of {@link FirstMatchingCompositeTimerRecordingHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        public FirstMatchingCompositeTimerRecordingHandler(TimerRecordingHandler... handlers) {
            this(Arrays.asList(handlers));
        }

        /**
         * Creates a new instance of {@link FirstMatchingCompositeTimerRecordingHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        public FirstMatchingCompositeTimerRecordingHandler(List<TimerRecordingHandler> handlers) {
            this.handlers = handlers;
        }

        @Override
        public void onStart(Timer.Sample sample, @Nullable Timer.HandlerContext context) {
            getFirstApplicableListener(context).ifPresent(listener -> listener.onStart(sample, context));
        }

        @Override
        public void onError(Timer.Sample sample, @Nullable Timer.HandlerContext context, Throwable throwable) {
            getFirstApplicableListener(context).ifPresent(listener -> listener.onError(sample, context, throwable));
        }

        @Override
        public void onScopeOpened(Timer.Sample sample, @Nullable Timer.HandlerContext context) {
            getFirstApplicableListener(context).ifPresent(listener -> listener.onScopeOpened(sample, context));
        }

        @Override
        public void onStop(Timer.Sample sample, @Nullable Timer.HandlerContext context, Timer timer, Duration duration) {
            getFirstApplicableListener(context).ifPresent(listener -> listener.onStop(sample, context, timer, duration));
        }

        private Optional<TimerRecordingHandler> getFirstApplicableListener(@Nullable Timer.HandlerContext context) {
            return this.handlers.stream().filter(handler -> handler.supportsContext(context)).findFirst();
        }

        @Override
        public boolean supportsContext(@Nullable Timer.HandlerContext handlerContext) {
            return getFirstApplicableListener(handlerContext).isPresent();
        }

        @Override
        public List<TimerRecordingHandler> getHandlers() {
            return this.handlers;
        }
    }

    /**
     * Handler picking all matching recording handlers from the list.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    class AllMatchingCompositeTimerRecordingHandler implements CompositeTimerRecordingHandler {

        private final List<TimerRecordingHandler> handlers;

        /**
         * Creates a new instance of {@link FirstMatchingCompositeTimerRecordingHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        public AllMatchingCompositeTimerRecordingHandler(TimerRecordingHandler... handlers) {
            this(Arrays.asList(handlers));
        }

        /**
         * Creates a new instance of {@link FirstMatchingCompositeTimerRecordingHandler}.
         * @param handlers the handlers that are registered under the composite
         */
        public AllMatchingCompositeTimerRecordingHandler(List<TimerRecordingHandler> handlers) {
            this.handlers = handlers;
        }

        @Override
        public void onStart(Timer.Sample sample, @Nullable Timer.HandlerContext context) {
            getAllApplicableListeners(context).forEach(listener -> listener.onStart(sample, context));
        }

        @Override
        public void onError(Timer.Sample sample, @Nullable Timer.HandlerContext context, Throwable throwable) {
            getAllApplicableListeners(context).forEach(listener -> listener.onError(sample, context, throwable));
        }

        @Override
        public void onScopeOpened(Timer.Sample sample, @Nullable Timer.HandlerContext context) {
            getAllApplicableListeners(context).forEach(listener -> listener.onScopeOpened(sample, context));
        }

        @Override
        public void onStop(Timer.Sample sample, @Nullable Timer.HandlerContext context, Timer timer, Duration duration) {
            getAllApplicableListeners(context).forEach(listener -> listener.onStop(sample, context, timer, duration));
        }

        private List<TimerRecordingHandler> getAllApplicableListeners(@Nullable Timer.HandlerContext context) {
            return this.handlers.stream().filter(handler -> handler.supportsContext(context)).collect(Collectors.toList());
        }

        @Override
        public boolean supportsContext(@Nullable Timer.HandlerContext handlerContext) {
            return !getAllApplicableListeners(handlerContext).isEmpty();
        }

        @Override
        public List<TimerRecordingHandler> getHandlers() {
            return this.handlers;
        }
    }
}
