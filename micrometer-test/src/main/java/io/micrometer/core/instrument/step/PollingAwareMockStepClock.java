/*
 * Copyright 2023 VMware, Inc.
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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.TimeUtils;

/**
 * A clock meant to be used for testing {@link StepMeterRegistry}. This clock does the
 * {@link StepMeterRegistry#pollMetersToRollover()} whenever the step is crossed thus
 * simulating the expected behaviour of step meters.
 *
 * @author Lenin Jaganathan
 * @since 1.11.1
 */
@Incubating(since = "1.11.1")
public class PollingAwareMockStepClock implements Clock {

    private final Duration step;

    private long timeNanos = (long) TimeUtils.millisToUnit(1, TimeUnit.NANOSECONDS);

    public PollingAwareMockStepClock(StepRegistryConfig stepRegistryConfig) {
        this.step = stepRegistryConfig.step();
    }

    @Override
    public long wallTime() {
        return MILLISECONDS.convert(timeNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public long monotonicTime() {
        return timeNanos;
    }

    /**
     * Advances clock by the duration specified and does
     * {@link StepMeterRegistry#pollMetersToRollover()} if necessary.
     * @param duration amount to increment by
     * @param stepMeterRegistry {@link StepMeterRegistry} to which clock is tied
     * @return current time after adding step
     */
    public long add(Duration duration, StepMeterRegistry stepMeterRegistry) {
        addTimeWithRolloverOnStepStart(duration, stepMeterRegistry);
        return timeNanos;
    }

    private void addTimeWithRolloverOnStepStart(Duration timeToAdd, StepMeterRegistry stepMeterRegistry) {
        long stepMillis = step.toMillis();
        while (timeToAdd.toMillis() >= stepMillis) {
            long boundaryForNextStep = ((timeNanos / stepMillis) + 1) * stepMillis;
            Duration timeToNextStep = Duration.ofMillis(boundaryForNextStep - timeNanos);
            if (timeToAdd.toMillis() >= timeToNextStep.toMillis()) {
                timeToAdd = timeToAdd.minus(timeToNextStep);
                addTimeToClock(timeToNextStep);
                stepMeterRegistry.pollMetersToRollover();
            }
        }
        addTimeToClock(timeToAdd);
    }

    private void addTimeToClock(Duration duration) {
        timeNanos += duration.toNanos();
    }

}
