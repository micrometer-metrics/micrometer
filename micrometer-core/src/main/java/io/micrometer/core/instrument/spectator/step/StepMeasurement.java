/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.spectator.step;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Supplier;

class StepMeasurement extends Measurement {
    private final StepDouble value;
    private final DoubleAdder lastCount = new DoubleAdder();
    private final Supplier<Double> f;

    public StepMeasurement(Supplier<Double> f, Statistic statistic, Clock clock, long step) {
        super(f, statistic);
        this.f = f;
        this.value = new StepDouble(clock, step);
    }

    @Override
    public double getValue() {
        double absoluteCount = f.get();
        double inc = Math.max(0, absoluteCount - lastCount.sum());
        lastCount.add(inc);
        value.getCurrent().add(inc);

        return value.poll();
    }

    /**
     * Subtly different from {@link com.netflix.spectator.impl.StepDouble} in that we want to be able
     * to increment BEFORE rolling over the interval.
     */
    private class StepDouble {
        private final Clock clock;
        private final long step;

        private volatile double previous = 0.0;
        private final DoubleAdder current = new DoubleAdder();

        private final AtomicLong lastInitPos;

        StepDouble(Clock clock, long step) {
            this.clock = clock;
            this.step = step;
            lastInitPos = new AtomicLong(clock.wallTime() / step);
        }

        private void rollCount(long now) {
            final long stepTime = now / step;
            final long lastInit = lastInitPos.get();
            if (lastInit < stepTime && lastInitPos.compareAndSet(lastInit, stepTime)) {
                final double v = current.sumThenReset();
                // Need to check if there was any activity during the previous step interval. If there was
                // then the init position will move forward by 1, otherwise it will be older. No activity
                // means the previous interval should be set to the `init` value.
                previous = (lastInit == stepTime - 1) ? v : 0.0;
            }
        }

        public DoubleAdder getCurrent() {
            return current;
        }

        /** Get the value for the last completed interval. */
        double poll() {
            rollCount(clock.wallTime());
            return previous;
        }
    }
}
