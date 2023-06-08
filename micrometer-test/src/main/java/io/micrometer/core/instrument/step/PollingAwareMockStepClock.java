package io.micrometer.core.instrument.step;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.TimeUtils;

/**
 * A clock meant to be used for testing {@link StepMeterRegistry}. This clock does the
 * {@link StepMeterRegistry#pollMetersToRollover()} whenever the step is crossed thus
 * simulating the expected behaviour of step meters.
 */
public class PollingAwareMockStepClock implements Clock {

    private final Duration step;

    private long timeNanos = (long) TimeUtils.millisToUnit(1, TimeUnit.NANOSECONDS);

    public PollingAwareMockStepClock(final StepRegistryConfig stepRegistryConfig) {
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
     * @param duration - amount to increment by.
     * @param stepMeterRegistry - {@link StepMeterRegistry} to which clock is tied to.
     * @return current time after adding step.
     */
    public long add(Duration duration, StepMeterRegistry stepMeterRegistry) {
        addTimeWithRolloverOnStepStart(duration, stepMeterRegistry);
        return timeNanos;
    }

    private void addTimeWithRolloverOnStepStart(Duration timeToAdd, StepMeterRegistry stepMeterRegistry) {
        while (timeToAdd.toMillis() >= step.toMillis()) {
            long boundaryForNextStep = ((timeNanos / step.toMillis()) + 1) * step.toMillis();
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
