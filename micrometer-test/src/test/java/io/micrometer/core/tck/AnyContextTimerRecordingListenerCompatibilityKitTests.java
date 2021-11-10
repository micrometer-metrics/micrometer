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
