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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.noop.NoopTimer;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

class CompositeTimer extends AbstractCompositeMeter<Timer> implements Timer {

    private final Clock clock;

    private final DistributionStatisticConfig distributionStatisticConfig;

    private final PauseDetector pauseDetector;

    CompositeTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        super(id);
        this.clock = clock;
        this.distributionStatisticConfig = distributionStatisticConfig;
        this.pauseDetector = pauseDetector;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        for (Timer timer : getChildren()) {
            timer.record(amount, unit);
        }
    }

    @Override
    public void record(Duration duration) {
        for (Timer timer : getChildren()) {
            timer.record(duration);
        }
    }

    @Override
    public <T> T record(Supplier<T> f) {
        final long s = clock.monotonicTime();
        try {
            return f.get();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public boolean record(BooleanSupplier f) {
        final long s = clock.monotonicTime();
        try {
            return f.getAsBoolean();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public int record(IntSupplier f) {
        final long s = clock.monotonicTime();
        try {
            return f.getAsInt();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public long record(LongSupplier f) {
        final long s = clock.monotonicTime();
        try {
            return f.getAsLong();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public double record(DoubleSupplier f) {
        final long s = clock.monotonicTime();
        try {
            return f.getAsDouble();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        final long s = clock.monotonicTime();
        try {
            return f.call();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void record(Runnable f) {
        final long s = clock.monotonicTime();
        try {
            f.run();
        }
        finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public long count() {
        return firstChild().count();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return firstChild().totalTime(unit);
    }

    @Override
    public double max(TimeUnit unit) {
        return firstChild().max(unit);
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        return firstChild().takeSnapshot();
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return firstChild().baseTimeUnit();
    }

    @Override
    Timer newNoopMeter() {
        return new NoopTimer(getId());
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    Timer registerNewMeter(MeterRegistry registry) {
        Timer.Builder builder = Timer.builder(getId().getName())
            .tags(getId().getTagsAsIterable())
            .description(getId().getDescription())
            .maximumExpectedValue(
                    Duration.ofNanos(distributionStatisticConfig.getMaximumExpectedValueAsDouble().longValue()))
            .minimumExpectedValue(
                    Duration.ofNanos(distributionStatisticConfig.getMinimumExpectedValueAsDouble().longValue()))
            .publishPercentiles(distributionStatisticConfig.getPercentiles())
            .publishPercentileHistogram(distributionStatisticConfig.isPercentileHistogram())
            .distributionStatisticBufferLength(distributionStatisticConfig.getBufferLength())
            .distributionStatisticExpiry(distributionStatisticConfig.getExpiry())
            .percentilePrecision(distributionStatisticConfig.getPercentilePrecision())
            .pauseDetector(pauseDetector);

        final double[] sloNanos = distributionStatisticConfig.getServiceLevelObjectiveBoundaries();
        if (sloNanos != null) {
            Duration[] slo = new Duration[sloNanos.length];
            for (int i = 0; i < sloNanos.length; i++) {
                slo[i] = Duration.ofNanos((long) sloNanos[i]);
            }
            builder = builder.serviceLevelObjectives(slo);
        }

        return builder.register(registry);
    }

}
