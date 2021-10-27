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

import io.micrometer.core.lang.Nullable;

/**
 * Listener with callbacks for the {@link Timer#start(MeterRegistry) start} and
 * {@link io.micrometer.core.instrument.Timer.Sample#stop(Timer) stop} of a {@link Timer} recording.
 */
public interface TimerRecordingListener<T extends Timer.Context> {
    /**
     * @param sample the sample that was started
     */
    void onStart(Timer.Sample sample, @Nullable T context);

    /**
     * @param sample sample for which the error happened
     * @param throwable exception that happened during recording
     */
    void onError(Timer.Sample sample, @Nullable T context, Throwable throwable);

    /**
     * @param sample the sample that was stopped
     * @param timer the timer to which the recording was made
     * @param duration time recorded
     */
    void onStop(Timer.Sample sample, @Nullable T context, Timer timer, Duration duration);

    boolean supportsContext(@Nullable Timer.Context context);
}
