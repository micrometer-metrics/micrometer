/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.Clock;

/**
 * A Clock that provides the capability to freeze it at a particular time. Once, the clock
 * is frozen to a particular time any call to {@link Clock#wallTime()} will return the
 * frozen time. This can be useful when closing the StepRegistry to roll over the clock
 * time to next nearest step.
 *
 * @author Lenin Jaganathan
 */
public class StoppableClock implements Clock {

    private final Clock clock;

    private long currentTimeInMs = 0;

    private boolean isClockStopped = false;

    public StoppableClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Stops the clock i,e any further call to {@link Clock#wallTime()} will return the
     * value passed in param.
     * @param currentTimeInMs
     */
    void stopClock(long currentTimeInMs) {
        this.isClockStopped = true;
        this.currentTimeInMs = currentTimeInMs;
    }

    void restartClock() {
        this.isClockStopped = false;
    }

    public Clock getOriginalClock() {
        return this.clock;
    }

    @Override
    public long wallTime() {
        if (isClockStopped) {
            return currentTimeInMs;
        }
        return clock.wallTime();
    }

    @Override
    public long monotonicTime() {
        return clock.monotonicTime();
    }

}
