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
package org.springframework.metrics.instrument.internal;

import org.springframework.metrics.instrument.*;
import org.springframework.metrics.instrument.stats.hist.Histogram;
import org.springframework.metrics.instrument.stats.quantile.Quantiles;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractMeterRegistry implements MeterRegistry {
    protected final Clock clock;
    protected AbstractMeterRegistry(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    @Override
    public Timer.Builder timerBuilder(String name) {
        return new TimerBuilder(name);
    }

    private class TimerBuilder implements Timer.Builder {
        private final String name;
        private Quantiles quantiles;
        private Histogram<?> histogram;
        private final List<Tag> tags = new ArrayList<>();

        private TimerBuilder(String name) {
            this.name = name;
        }

        @Override
        public Timer.Builder quantiles(Quantiles quantiles) {
            this.quantiles = quantiles;
            return this;
        }

        public Timer.Builder histogram(Histogram histogram) {
            this.histogram = histogram;
            return this;
        }

        @Override
        public Timer.Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        @Override
        public Timer create() {
            return timer(name, tags, quantiles, histogram);
        }
    }

    protected abstract Timer timer(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram);

    @Override
    public DistributionSummary.Builder summaryBuilder(String name) {
        return new DistributionSummaryBuilder(name);
    }

    private class DistributionSummaryBuilder implements DistributionSummary.Builder {
        private final String name;
        private Quantiles quantiles;
        private Histogram<?> histogram;
        private final List<Tag> tags = new ArrayList<>();

        private DistributionSummaryBuilder(String name) {
            this.name = name;
        }

        @Override
        public DistributionSummary.Builder quantiles(Quantiles quantiles) {
            this.quantiles = quantiles;
            return this;
        }

        public DistributionSummary.Builder histogram(Histogram<?> histogram) {
            this.histogram = histogram;
            return this;
        }

        @Override
        public DistributionSummary.Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        @Override
        public DistributionSummary create() {
            return distributionSummary(name, tags, quantiles, histogram);
        }
    }

    protected abstract DistributionSummary distributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram);
}
