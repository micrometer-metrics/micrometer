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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Meter;
import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.FunctionCounter;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;

/**
 * @author Jon Schneider
 */
public class DropwizardFunctionCounter<T> extends AbstractMeter implements FunctionCounter {

    private final WeakReference<T> ref;

    private final ToDoubleFunction<T> f;

    private final AtomicLong last = new AtomicLong();

    private final DropwizardRate rate;

    private final Meter dropwizardMeter;

    DropwizardFunctionCounter(Id id, Clock clock, T obj, ToDoubleFunction<T> f) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.f = f;
        this.rate = new DropwizardRate(clock);
        this.dropwizardMeter = new Meter(new DropwizardClock(clock)) {
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
        };
    }

    public Meter getDropwizardMeter() {
        return dropwizardMeter;
    }

    @Override
    public double count() {
        T obj = ref.get();
        if (obj == null)
            return last.get();
        return last.updateAndGet(prev -> {
            long newCount = (long) f.applyAsDouble(obj);
            long diff = newCount - prev;
            rate.increment(diff);
            return newCount;
        });
    }

}
