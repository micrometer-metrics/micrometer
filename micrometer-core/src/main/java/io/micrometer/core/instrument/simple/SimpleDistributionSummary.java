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

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Observer;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.util.MeterId;

import java.util.Arrays;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class SimpleDistributionSummary extends AbstractDistributionSummary {
    private static final Tag STAT_COUNT_TAG = Tag.of("statistic", "count");
    private static final Tag STAT_AMOUNT_TAG = Tag.of("statistic", "amount");
    private static final Tag TYPE_TAG = SimpleUtils.typeTag(Type.DistributionSummary);

    private final MeterId countId;
    private final MeterId amountId;

    private LongAdder count = new LongAdder();
    private DoubleAdder amount = new DoubleAdder();

    public SimpleDistributionSummary(MeterId id, Observer... observers) {
        super(id, observers);
        this.countId = id.withTags(TYPE_TAG, STAT_COUNT_TAG);
        this.amountId = id.withTags(TYPE_TAG, STAT_AMOUNT_TAG);
    }

    @Override
    public void recordSummary(double amount) {
        if (amount >= 0) {
            count.increment();
            this.amount.add(amount);
        }
    }

    @Override
    public long count() {
        return count.longValue();
    }

    @Override
    public double totalAmount() {
        return amount.doubleValue();
    }

    @Override
    public String getName() {
        return id.getName();
    }

    @Override
    public Iterable<Tag> getTags() {
        return id.getTags();
    }

    @Override
    public Iterable<Measurement> measure() {
        return Arrays.asList(
                countId.measurement(count()),
                amountId.measurement(totalAmount()));
    }
}
