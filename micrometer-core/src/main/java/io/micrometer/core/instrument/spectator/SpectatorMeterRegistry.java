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
package io.micrometer.core.instrument.spectator;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public abstract class SpectatorMeterRegistry extends AbstractMeterRegistry {
    private final Registry registry;

    public SpectatorMeterRegistry(Registry registry, Clock clock) {
        super(clock);
        this.registry = registry;
    }

    private Collection<com.netflix.spectator.api.Tag> toSpectatorTags(Iterable<io.micrometer.core.instrument.Tag> tags) {
        return stream(tags.spliterator(), false)
            .map(t -> new BasicTag(t.getKey(), t.getValue()))
            .collect(toList());
    }

    @Override
    protected io.micrometer.core.instrument.Counter newCounter(String name, Iterable<Tag> tags, String description) {
        com.netflix.spectator.api.Counter counter = registry.counter(name, toSpectatorTags(tags));
        return new SpectatorCounter(counter, description);
    }

    @Override
    protected io.micrometer.core.instrument.DistributionSummary newDistributionSummary(String name, Iterable<Tag> tags, String description, Quantiles quantiles, Histogram<?> histogram) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles, UnaryOperator.identity());
        com.netflix.spectator.api.DistributionSummary ds = registry.distributionSummary(name, toSpectatorTags(tags));
        return new SpectatorDistributionSummary(ds, description);
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(String name, Iterable<Tag> tags, String description, Histogram<?> histogram, Quantiles quantiles) {
        // scale nanosecond precise quantile values to seconds
        registerQuantilesGaugeIfNecessary(name, tags, quantiles, t -> t / 1.0e6);
        registerHistogramCounterIfNecessary(name, tags, histogram);
        com.netflix.spectator.api.Timer timer = registry.timer(name, toSpectatorTags(tags));
        return new SpectatorTimer(timer, description, quantiles, config().clock());
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(String name, Iterable<Tag> tags, String description, ToDoubleFunction<T> f, T obj) {
        Id gaugeId = registry.createId(name, toSpectatorTags(tags));
        com.netflix.spectator.api.Gauge gauge = new CustomSpectatorToDoubleGauge<>(registry.clock(), gaugeId, obj, f);
        registry.register(gauge);
        return new SpectatorGauge(gauge, description);
    }

    private void registerHistogramCounterIfNecessary(String name, Iterable<io.micrometer.core.instrument.Tag> tags, Histogram<?> histogram) {
        // FIXME need the heisen-counter to complete
//        if(histogram != null) {
//            for (Bucket<?> bucket : histogram.getBuckets()) {
//                List<com.netflix.spectator.api.Tag> histogramTags = new LinkedList<>(toSpectatorTags(tags));
//                histogramTags.add(new BasicTag("bucket", bucket.toString()));
//                histogramTags.add(new BasicTag("statistic", "histogram"));
//                registry.counter(registry.createId(tagFormatter.formatName(name), histogramTags)).count();
//            }
//        }
    }

    private void registerQuantilesGaugeIfNecessary(String name, Iterable<io.micrometer.core.instrument.Tag> tags, Quantiles quantiles, UnaryOperator<Double> scaling) {
        if (quantiles != null) {
            for (Double q : quantiles.monitored()) {
                List<com.netflix.spectator.api.Tag> quantileTags = new LinkedList<>(toSpectatorTags(tags));
                if (!Double.isNaN(q)) {
                    quantileTags.add(new BasicTag("quantile", Double.toString(q)));
                    quantileTags.add(new BasicTag("statistic", "value"));
                    registry.gauge(registry.createId(name, quantileTags), q, q2 -> scaling.apply(quantiles.get(q2)));
                }
            }
        }
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(String name, Iterable<Tag> tags, String description) {
        com.netflix.spectator.api.LongTaskTimer timer = registry.longTaskTimer(name, toSpectatorTags(tags));
        return new SpectatorLongTaskTimer(timer, description);
    }

    @Override
    protected void newMeter(String name, Iterable<Tag> tags, Meter.Type type, Iterable<io.micrometer.core.instrument.Measurement> measurements) {
        Id spectatorId = spectatorId(registry, name, tags);
        com.netflix.spectator.api.AbstractMeter<Id> spectatorMeter = new com.netflix.spectator.api.AbstractMeter<Id>(registry.clock(), spectatorId, spectatorId) {
            @Override
            public Iterable<Measurement> measure() {
                return stream(measurements.spliterator(), false)
                    .map(m -> new Measurement(spectatorId(registry, name, tags), clock.wallTime(), m.getValue()))
                    .collect(toList());
            }
        };
        registry.register(spectatorMeter);
    }

    /**
     * @return The underlying Spectator {@link Registry}.
     */
    public Registry getSpectatorRegistry() {
        return registry;
    }

    private static Id spectatorId(Registry registry, String name, Iterable<Tag> tags) {
        String[] flattenedTags = stream(tags.spliterator(), false)
            .flatMap(t -> Stream.of(t.getKey(), t.getValue()))
            .toArray(String[]::new);
        return registry.createId(name, flattenedTags);
    }
}
