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

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Resettable {@link Timer} implementation for Dynatrace exporters.
 *
 * @author Georg Pirklbauer
 * @since 1.9.0
 */
public final class DynatraceTimer extends AbstractMeter implements Timer, DynatraceSummarySnapshotSupport {
    private final DynatraceSummary summary = new DynatraceSummary();
    private final Clock clock;
    private final TimeUnit baseTimeUnit;

    public DynatraceTimer(Id id, Clock clock, TimeUnit baseTimeUnit) {
        super(id);
        this.clock = clock;
        this.baseTimeUnit = baseTimeUnit;
    }

    @Override
    public boolean hasValues() {
        return count() > 0;
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot() {
        return takeSummarySnapshot(baseTimeUnit());
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshot(TimeUnit unit) {
        return new DynatraceSummarySnapshot(min(unit), max(unit), totalTime(unit), count());
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset() {
        return takeSummarySnapshotAndReset(baseTimeUnit());
    }

    @Override
    public DynatraceSummarySnapshot takeSummarySnapshotAndReset(TimeUnit unit) {
        DynatraceSummarySnapshot snapshot = takeSummarySnapshot(unit);
        summary.reset();
        return snapshot;
    }

    // from AbstractTimer
    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        final long s = clock.monotonicTime();
        try {
            return f.call();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    // from AbstractTimer
    @Override
    public <T> T record(Supplier<T> f) {
        final long s = clock.monotonicTime();
        try {
            return f.get();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    // from AbstractTimer
    @Override
    public void record(Runnable f) {
        final long s = clock.monotonicTime();
        try {
            f.run();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        // store everything in baseTimeUnit
        long inBaseUnit = baseTimeUnit().convert(amount, unit);
        summary.recordNonNegative(inBaseUnit);
    }

    @Override
    public long count() {
        return summary.getCount();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return unit.convert((long) summary.getTotal(), baseTimeUnit());
    }

    @Override
    public double max(TimeUnit unit) {
        return unit.convert((long) summary.getMax(), baseTimeUnit());
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return baseTimeUnit;
    }

    public double min(TimeUnit unit) {
        return unit.convert((long) summary.getMin(), baseTimeUnit());
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        DynatraceSummarySnapshot dtSnapshot = takeSummarySnapshot();
        return HistogramSnapshot.empty(dtSnapshot.getCount(), dtSnapshot.getTotal(), dtSnapshot.getMax());
    }
}
