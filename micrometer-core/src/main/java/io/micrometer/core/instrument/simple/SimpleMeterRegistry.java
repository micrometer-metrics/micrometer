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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.LinkedList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * A minimal meter registry implementation primarily used for tests.
 *
 * @author Jon Schneider
 */
public class SimpleMeterRegistry extends AbstractMeterRegistry {
    public SimpleMeterRegistry() {
        this(Clock.SYSTEM);
    }

    public SimpleMeterRegistry(Clock clock) {
        super(clock);
    }

    @Override
    protected Counter newCounter(String name, Iterable<Tag> tags, String description) {
        return new SimpleCounter(name, tags, description);
    }

    @Override
    protected DistributionSummary newDistributionSummary(String name, Iterable<Tag> tags, String description, Quantiles quantiles, Histogram<?> histogram) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles);
        return new SimpleDistributionSummary(name, tags, description);
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(String name, Iterable<Tag> tags, String description, Histogram<?> histogram, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles);
        return new SimpleTimer(name, tags, description, config().clock());
    }

    @Override
    protected <T> Gauge newGauge(String name, Iterable<Tag> tags, String description, ToDoubleFunction<T> f, T obj) {
        return new SimpleGauge<>(name, tags, description, obj, f);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(String name, Iterable<Tag> tags, String description) {
        return new SimpleLongTaskTimer(name, tags, description, config().clock());
    }

    private void registerQuantilesGaugeIfNecessary(String name, Iterable<Tag> tags, Quantiles quantiles) {
        if (quantiles != null) {
            for (Double q : quantiles.monitored()) {
                List<Tag> quantileTags = new LinkedList<>();
                tags.forEach(quantileTags::add);
                quantileTags.add(Tag.of("quantile", Double.isNaN(q) ? "NaN" : Double.toString(q)));
                gauge(name, quantileTags, q, quantiles::get);
            }
        }
    }

    @Override
    protected void newMeter(String name, Iterable<Tag> tags, Meter.Type type, Iterable<Measurement> measurements) {
        // do nothing, the meter is already registered
    }
}
