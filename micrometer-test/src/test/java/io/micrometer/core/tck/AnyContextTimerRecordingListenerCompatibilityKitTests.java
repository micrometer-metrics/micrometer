package io.micrometer.core.tck;

import java.time.Duration;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.TimerRecordingListener;

class AnyContextTimerRecordingListenerCompatibilityKitTests extends AnyContextTimerRecordingListenerCompatibilityKit {

    @Override
    public TimerRecordingListener<Timer.Context> listener() {
        return new TimerRecordingListener<Timer.Context>() {
            @Override
            public void onStart(Timer.Sample sample, Timer.Context context) {

            }

            @Override
            public void onError(Timer.Sample sample, Timer.Context context, Throwable throwable) {

            }

            @Override
            public void onRestore(Timer.Sample sample, Timer.Context context) {

            }

            @Override
            public void onStop(Timer.Sample sample, Timer.Context context, Timer timer, Duration duration) {

            }

            @Override
            public boolean supportsContext(Timer.Context context) {
                return true;
            }
        };
    }
}
