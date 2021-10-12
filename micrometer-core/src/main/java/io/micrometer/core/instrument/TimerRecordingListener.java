package io.micrometer.core.instrument;

import java.time.Duration;

/**
 * Listener with callbacks for the {@link Timer#start(MeterRegistry) start} and
 * {@link io.micrometer.core.instrument.Timer.Sample#stop(Timer) stop} of a {@link Timer} recording.
 */
public interface TimerRecordingListener {
    /**
     * @param sample the sample that was started
     */
    void onStart(Timer.Sample sample);

    /**
     * @param sample sample for which the error happened
     * @param throwable exception that happened during recording
     */
    void onError(Timer.Sample sample, Throwable throwable);

    /**
     * @param sample the sample that was stopped
     * @param timer the timer to which the recording was made
     * @param duration time recorded
     */
    void onStop(Timer.Sample sample, Timer timer, Duration duration);
}
