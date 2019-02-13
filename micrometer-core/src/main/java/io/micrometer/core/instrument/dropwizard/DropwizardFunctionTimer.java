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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.util.TimeUtils;

import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * {@link FunctionTimer} for Dropwizard Metrics.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class DropwizardFunctionTimer<T> extends AbstractMeter implements FunctionTimer {
    private final WeakReference<T> ref;
    private final ToLongFunction<T> countFunction;
    private final ToDoubleFunction<T> totalTimeFunction;
    private final TimeUnit totalTimeFunctionUnit;

    private final AtomicLong lastCount = new AtomicLong(0);
    private final DropwizardRate rate;
    private final Timer dropwizardMeter;
    private final TimeUnit registryBaseTimeUnit;
    private volatile double lastTime = 0.0;

    DropwizardFunctionTimer(Meter.Id id, Clock clock,
                            T obj, ToLongFunction<T> countFunction,
                            ToDoubleFunction<T> totalTimeFunction,
                            TimeUnit totalTimeFunctionUnit,
                            TimeUnit registryBaseTimeUnit) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.countFunction = countFunction;
        this.totalTimeFunction = totalTimeFunction;
        this.totalTimeFunctionUnit = totalTimeFunctionUnit;
        this.rate = new DropwizardRate(clock);
        this.registryBaseTimeUnit = registryBaseTimeUnit;
        this.dropwizardMeter = new Timer(null, new DropwizardClock(clock)) {
            @Override
            public double getFifteenMinuteRate() {
                count();
                return rate.getFifteenMinuteRate();
            }

            @Override
            public double getFiveMinuteRate() {
                count();
                return rate.getFiveMinuteRate();
            }

            @Override
            public double getOneMinuteRate() {
                count();
                return rate.getOneMinuteRate();
            }

            @Override
            public long getCount() {
                return (long) count();
            }

            @Override
            public Snapshot getSnapshot() {
                return new Snapshot() {
                    @Override
                    public double getValue(double quantile) {
                        return quantile == 0.5 ? getMean() : 0;
                    }

                    @Override
                    public long[] getValues() {
                        return new long[0];
                    }

                    @Override
                    public int size() {
                        return 1;
                    }

                    @Override
                    public long getMax() {
                        return 0;
                    }

                    @Override
                    public double getMean() {
                        double count = count();
                        // This return value is expected to be in nanoseconds, for example in JmxReporter.JmxTimer.
                        return count == 0 ? 0 : totalTime(TimeUnit.NANOSECONDS) / count;
                    }

                    @Override
                    public long getMin() {
                        return 0;
                    }

                    @Override
                    public double getStdDev() {
                        return 0;
                    }

                    @Override
                    public void dump(OutputStream output) {
                    }
                };
            }
        };
    }

    public Timer getDropwizardMeter() {
        return dropwizardMeter;
    }

    @Override
    public double count() {
        T obj = ref.get();
        if (obj == null)
            return lastCount.get();
        return lastCount.updateAndGet(prev -> {
            long newCount = countFunction.applyAsLong(obj);
            long diff = newCount - prev;
            rate.increment(diff);
            return newCount;
        });
    }

    @Override
    public double totalTime(TimeUnit unit) {
        T obj2 = ref.get();
        if (obj2 != null) {
            lastTime = TimeUtils.convert(totalTimeFunction.applyAsDouble(obj2), totalTimeFunctionUnit, baseTimeUnit());
        }
        return TimeUtils.convert(lastTime, baseTimeUnit(), unit);
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return registryBaseTimeUnit;
    }
}
