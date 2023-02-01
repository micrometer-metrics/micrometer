/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.dynatrace.types;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Internal class for resettable summary statistics.
 *
 * @author Georg Pirklbauer
 */
final class DynatraceSummary {

    private final LongAdder count = new LongAdder();

    private final DoubleAdder total = new DoubleAdder();

    private final AtomicLong min = new AtomicLong();

    private final AtomicLong max = new AtomicLong();

    void recordNonNegative(double amount) {
        if (amount < 0) {
            return;
        }

        long longBits = Double.doubleToLongBits(amount);
        synchronized (this) {
            max.getAndUpdate(prev -> Math.max(prev, longBits));
            // have to check if a value was already recorded before, otherwise min will
            // always stay 0 (because the default is 0).
            min.getAndUpdate(prev -> count.longValue() > 0 ? Math.min(prev, longBits) : longBits);

            total.add(amount);
            count.increment();
        }
    }

    long getCount() {
        return count.longValue();
    }

    double getTotal() {
        return total.doubleValue();
    }

    double getMin() {
        return Double.longBitsToDouble(min.longValue());
    }

    double getMax() {
        return Double.longBitsToDouble(max.longValue());
    }

    DynatraceSummarySnapshot takeSummarySnapshot() {
        synchronized (this) {
            return new DynatraceSummarySnapshot(getMin(), getMax(), getTotal(), getCount());
        }
    }

    DynatraceSummarySnapshot takeSummarySnapshotAndReset() {
        synchronized (this) {
            DynatraceSummarySnapshot snapshot = takeSummarySnapshot();
            reset();
            return snapshot;
        }
    }

    void reset() {
        synchronized (this) {
            min.set(0);
            max.set(0);
            total.reset();
            count.reset();
        }
    }

}
