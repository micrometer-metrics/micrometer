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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Timer;
import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DropwizardTimer extends AbstractTimer {
    private final Timer impl;
    private final AtomicLong totalTime = new AtomicLong(0);

    DropwizardTimer(Meter.Id id, String description, Timer impl, Clock clock) {
        super(id, description, clock);
        this.impl = impl;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        if (amount >= 0) {
            impl.update(amount, unit);
            totalTime.addAndGet(TimeUnit.NANOSECONDS.convert(amount, unit));
        }
    }

    @Override
    public long count() {
        return impl.getCount();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return TimeUtils.convert(totalTime.get(), TimeUnit.NANOSECONDS, unit);
    }
}
