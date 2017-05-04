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
package org.springframework.metrics.instrument.simple;

import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.internal.AbstractTimer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jon Schneider
 */
public class SimpleTimer extends AbstractTimer {
    private AtomicLong count = new AtomicLong(0);
    private AtomicLong totalTime = new AtomicLong(0);

    public SimpleTimer() {
        this(Clock.SYSTEM);
    }

    public SimpleTimer(Clock clock) {
        super(clock);
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        count.incrementAndGet();
        totalTime.addAndGet(TimeUnit.NANOSECONDS.convert(amount, unit));
    }

    @Override
    public long count() {
        return count.get();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return nanosToUnit(totalTime.get(), unit);
    }
}
