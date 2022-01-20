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
package io.micrometer.core.tck;

import java.time.Duration;

import io.micrometer.api.instrument.Timer;
import io.micrometer.api.instrument.TimerRecordingHandler;

class NullContextTimerRecordingHandlerCompatibilityKitTests extends NullHandlerContextTimerRecordingHandlerCompatibilityKit {

    @Override
    public TimerRecordingHandler<Timer.HandlerContext> handler() {
        return new TimerRecordingHandler<Timer.HandlerContext>() {
            @Override
            public void onStart(Timer.Sample sample, Timer.HandlerContext handlerContext) {

            }

            @Override
            public void onError(Timer.Sample sample, Timer.HandlerContext handlerContext, Throwable throwable) {

            }

            @Override
            public void onScopeOpened(Timer.Sample sample, Timer.HandlerContext handlerContext) {

            }

            @Override
            public void onStop(Timer.Sample sample, Timer.HandlerContext handlerContext, Timer timer, Duration duration) {

            }

            @Override
            public boolean supportsContext(Timer.HandlerContext handlerContext) {
                return true;
            }
        };
    }
}
