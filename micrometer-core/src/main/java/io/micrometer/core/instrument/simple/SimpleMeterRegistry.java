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
    protected Counter newCounter(Meter.Id id, String description) {
        return new SimpleCounter(id, description);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, String description, Histogram<?> histogram, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(id, quantiles);
        return new SimpleDistributionSummary(id, description);
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id, String description, Histogram<?> histogram, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(id, quantiles);
        return new SimpleTimer(id, description, config().clock());
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, String description, ToDoubleFunction<T> f, T obj) {
        return new SimpleGauge<>(id, description, obj, f);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, String description) {
        return new SimpleLongTaskTimer(id, description, config().clock());
    }

    private void registerQuantilesGaugeIfNecessary(Meter.Id id, Quantiles quantiles) {
        if (quantiles != null) {
            for (Double q : quantiles.monitored()) {
                List<Tag> quantileTags = new LinkedList<>();
                id.getTags().forEach(quantileTags::add);
                quantileTags.add(Tag.of("quantile", Double.isNaN(q) ? "NaN" : Double.toString(q)));
                gauge(id.getName(), quantileTags, q, quantiles::get);
            }
        }
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        // do nothing, the meter is already registered
    }
}
