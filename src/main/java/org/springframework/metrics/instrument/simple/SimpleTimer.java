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
import org.springframework.metrics.instrument.Measurement;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.internal.AbstractTimer;
import org.springframework.metrics.instrument.internal.MeterId;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import static org.springframework.metrics.instrument.internal.TimeUtils.nanosToUnit;
import static org.springframework.metrics.instrument.simple.SimpleUtils.typeTag;

/**
 * @author Jon Schneider
 */
public class SimpleTimer extends AbstractTimer {
    private LongAdder count = new LongAdder();
    private LongAdder totalTime = new LongAdder();

    SimpleTimer(MeterId id, Clock clock) {
        super(id, clock);
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        if(amount >= 0) {
            count.increment();
            totalTime.add(TimeUnit.NANOSECONDS.convert(amount, unit));
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return nanosToUnit(totalTime.doubleValue(), unit);
    }

    @Override
    public Iterable<Measurement> measure() {
        return Arrays.asList(
                id.withTags(typeTag(getType()), Tag.of("statistic", "count")).measurement(count()),
                id.withTags(typeTag(getType()), Tag.of("statistic", "amount")).measurement(totalTime(TimeUnit.NANOSECONDS))
        );
    }
}
