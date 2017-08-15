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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static java.util.stream.StreamSupport.stream;

public abstract class AbstractMeterRegistry implements MeterRegistry {
    protected final Clock clock;
    protected final List<Tag> commonTags = new ArrayList<>();

    protected AbstractMeterRegistry(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    @Override
    public <T> Gauge.Builder gaugeBuilder(String name, T obj, ToDoubleFunction<T> f) {
        return new GaugeBuilder<>(name, obj, f);
    }

    private class GaugeBuilder<T> implements Gauge.Builder {
        private final String name;
        private final T obj;
        private final ToDoubleFunction<T> f;
        private final List<Tag> tags = new ArrayList<>();

        private GaugeBuilder(String name, T obj, ToDoubleFunction<T> f) {
            this.name = name;
            this.obj = obj;
            this.f = f;
        }

        @Override
        public Gauge.Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        @Override
        public Gauge create() {
            return newGauge(name, tags, obj, f);
        }
    }

    protected abstract <T> Gauge newGauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f);

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
            return newTimer(name, tags, quantiles, histogram);
        }
    }

    protected abstract Timer newTimer(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram);

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
            return newDistributionSummary(name, tags, quantiles, histogram);
        }
    }

    protected abstract DistributionSummary newDistributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles, Histogram<?> histogram);

    @Override
    public void commonTags(Iterable<Tag> tags) {
        tags.forEach(commonTags::add);
    }
}
