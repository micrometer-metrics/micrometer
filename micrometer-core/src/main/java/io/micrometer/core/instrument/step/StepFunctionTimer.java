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
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.util.TimeUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * A timer that tracks monotonically increasing functions for count and totalTime.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class StepFunctionTimer<T> implements FunctionTimer, StepMeter {

    private final Id id;

    private final WeakReference<T> ref;

    private final ToLongFunction<T> countFunction;

    private final ToDoubleFunction<T> totalTimeFunction;

    private final TimeUnit totalTimeFunctionUnit;

    private final TimeUnit baseTimeUnit;

    private final Clock clock;

    private volatile long lastUpdateTime = (long) (-2e6);

    private volatile long lastCount;

    private volatile double lastTime;

    private final LongAdder count = new LongAdder();

    private final DoubleAdder total = new DoubleAdder();

    private final StepTuple2<Long, Double> countTotal;

    public StepFunctionTimer(Id id, Clock clock, long stepMillis, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit, TimeUnit baseTimeUnit) {
        this.id = id;
        this.clock = clock;
        this.ref = new WeakReference<>(obj);
        this.countFunction = countFunction;
        this.totalTimeFunction = totalTimeFunction;
        this.totalTimeFunctionUnit = totalTimeFunctionUnit;
        this.baseTimeUnit = baseTimeUnit;
        this.countTotal = new StepTuple2<>(clock, stepMillis, 0L, 0.0, count::sumThenReset, total::sumThenReset);
    }

    /**
     * The total number of occurrences of the timed event.
     */
    public double count() {
        accumulateCountAndTotal();
        return countTotal.poll1();
    }

    /**
     * The total time of all occurrences of the timed event.
     */
    public double totalTime(TimeUnit unit) {
        accumulateCountAndTotal();
        return TimeUtils.convert(countTotal.poll2(), baseTimeUnit(), unit);
    }

    private void accumulateCountAndTotal() {
        T obj2 = ref.get();
        if (obj2 != null && clock.monotonicTime() - lastUpdateTime > 1e6) {
            long prevLastCount = lastCount;
            lastCount = Math.max(countFunction.applyAsLong(obj2), 0);
            count.add(lastCount - prevLastCount);

            double prevLastTime = lastTime;
            lastTime = Math.max(
                    TimeUtils.convert(totalTimeFunction.applyAsDouble(obj2), totalTimeFunctionUnit, baseTimeUnit()), 0);
            total.add(lastTime - prevLastTime);

            lastUpdateTime = clock.monotonicTime();
        }
    }

    @Override
    public Id getId() {
        return id;
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return this.baseTimeUnit;
    }

    public Type type() {
        return Type.TIMER;
    }

    @Override
    public void _closingRollover() {
        accumulateCountAndTotal();
        countTotal.closingRollover();
    }

}
