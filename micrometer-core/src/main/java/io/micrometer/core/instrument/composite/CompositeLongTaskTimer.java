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

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.noop.NoopLongTaskTimer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class CompositeLongTaskTimer extends AbstractCompositeMeter<LongTaskTimer> implements LongTaskTimer {

    private final DistributionStatisticConfig distributionStatisticConfig;

    CompositeLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        super(id);
        this.distributionStatisticConfig = distributionStatisticConfig;
    }

    @Override
    public Sample start() {
        List<Sample> samples = new ArrayList<>();
        for (LongTaskTimer ltt : getChildren()) {
            samples.add(ltt.start());
        }
        return new CompositeSample(samples);
    }

    @Override
    public double duration(TimeUnit unit) {
        return firstChild().duration(unit);
    }

    @Override
    public int activeTasks() {
        return firstChild().activeTasks();
    }

    @Override
    public double max(TimeUnit unit) {
        return firstChild().max(unit);
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        return firstChild().takeSnapshot();
    }

    @Override
    LongTaskTimer newNoopMeter() {
        return new NoopLongTaskTimer(getId());
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    LongTaskTimer registerNewMeter(MeterRegistry registry) {
        LongTaskTimer.Builder builder = LongTaskTimer.builder(getId().getName())
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
            .percentilePrecision(distributionStatisticConfig.getPercentilePrecision());

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

    static class CompositeSample extends Sample {

        private final List<Sample> samples;

        private CompositeSample(List<Sample> samples) {
            this.samples = samples;
        }

        @Override
        public long stop() {
            return samples.stream().reduce(0L, (stopped, sample) -> sample.stop(), (s1, s2) -> s1);
        }

        @Override
        public double duration(TimeUnit unit) {
            return samples.stream().findAny().map(s -> s.duration(unit)).orElse(0.0);
        }

    }

}
