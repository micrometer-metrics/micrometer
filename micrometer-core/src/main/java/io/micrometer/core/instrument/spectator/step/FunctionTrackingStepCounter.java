/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.spectator.step;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.impl.StepValue;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.ToDoubleFunction;

/**
 * Step counter that tracks increments in a monotonically increasing double function.
 *
 * @author Jon Schneider
 */
public class FunctionTrackingStepCounter<T> implements Counter {
    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> f;
    private final AtomicLong lastCount = new AtomicLong(0);
    private final StepDouble value;
    private final Id id;

    public FunctionTrackingStepCounter(Id id, Clock clock, long step, T obj, ToDoubleFunction<T> f) {
        this.id = id;
        this.value = new StepDouble(clock, step);
        this.ref = new WeakReference<>(obj);
        this.f = f;
    }

    @Override
    public void increment() {
        throw new UnsupportedOperationException("Should never be called directly");
    }

    @Override
    public void increment(long amount) {
        throw new UnsupportedOperationException("Should never be called directly.");
    }

    @Override
    public long count() {
        pollFunction();
        return (long) value.poll();
    }

    private void pollFunction() {
        if(ref.get() != null) {
            long absoluteCount = (long) f.applyAsDouble(ref.get());
            long inc = Math.max(0, absoluteCount - lastCount.get());
            lastCount.addAndGet(inc);
            value.getCurrent().add(inc);
        }
    }

    @Override
    public Id id() {
        return id;
    }

    @Override
    public Iterable<Measurement> measure() {
        pollFunction();
        final double rate = value.pollAsRate();
        final Measurement m = new Measurement(id, value.timestamp(), rate);
        return Collections.singletonList(m);
    }

    @Override
    public boolean hasExpired() {
        return ref.get() != null;
    }

    /**
     * Subtly different from {@link com.netflix.spectator.impl.StepDouble} in that we want to be able
     * to increment BEFORE rolling over the interval.
     */
    private class StepDouble implements StepValue {
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

        /** Get the value for the last completed interval as a rate per second. */
        @Override public double pollAsRate() {
            final double amount = poll();
            final double period = step / 1000.0;
            return amount / period;
        }

        /** Get the timestamp for the end of the last completed interval. */
        @Override public long timestamp() {
            return lastInitPos.get() * step;
        }
    }
}
