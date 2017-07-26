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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.util.MeterId;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.instrument.AbstractTimer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author Jon Schneider
 */
public class SimpleTimer extends AbstractTimer {
    private static final Tag STAT_COUNT_TAG = Tag.of("statistic", "count");
    private static final Tag STAT_AMOUNT_TAG = Tag.of("statistic", "amount");
    private static final Tag TYPE_TAG = SimpleUtils.typeTag(Type.Timer);

    private final MeterId countId;
    private final MeterId amountId;
    private LongAdder count = new LongAdder();
    private LongAdder totalTime = new LongAdder();

    public SimpleTimer(MeterId id, Clock clock) {
        super(id, clock);
        this.countId = id.withTags(TYPE_TAG, STAT_COUNT_TAG);
        this.amountId = id.withTags(TYPE_TAG, STAT_AMOUNT_TAG);
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        if (amount >= 0) {
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
        return TimeUtils.nanosToUnit(totalTime.doubleValue(), unit);
    }

    @Override
    public List<Measurement> measure() {
        return Arrays.asList(
                countId.measurement(count()),
                amountId.measurement(totalTime(TimeUnit.NANOSECONDS)));
    }
}
