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

import com.netflix.spectator.api.*;
import com.netflix.spectator.api.Measurement;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.AbstractMeterRegistry;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;
import static io.micrometer.core.instrument.spectator.SpectatorUtils.spectatorId;

/**
 * @author Jon Schneider
 */
public class SpectatorMeterRegistry extends AbstractMeterRegistry {
    private final ExternalClockSpectatorRegistry registry;
    private final Map<com.netflix.spectator.api.Meter, io.micrometer.core.instrument.Meter> meterMap = new HashMap<>();

    private final com.netflix.spectator.api.Clock spectatorClock = new com.netflix.spectator.api.Clock() {
        @Override
        public long wallTime() {
            // We don't see a need to provide a wallTime abstraction in our Clock interface. It only appears to be
            // used by Spectator to mark measurements on the way out the door.
            return com.netflix.spectator.api.Clock.SYSTEM.wallTime();
        }

        @Override
        public long monotonicTime() {
            return getClock().monotonicTime();
        }
    };

    public SpectatorMeterRegistry() {
        this(new DefaultRegistry());
    }

    public SpectatorMeterRegistry(Registry registry) {
        this(registry, io.micrometer.core.instrument.Clock.SYSTEM);
    }

    public SpectatorMeterRegistry(Registry registry, io.micrometer.core.instrument.Clock clock) {
        super(clock);
        this.registry = new ExternalClockSpectatorRegistry(registry, new com.netflix.spectator.api.Clock() {
            @Override
            public long wallTime() {
                return System.currentTimeMillis();
            }

            @Override
            public long monotonicTime() {
                return clock.monotonicTime();
            }
        });
    }

    private Collection<com.netflix.spectator.api.Tag> toSpectatorTags(Iterable<io.micrometer.core.instrument.Tag> tags) {
        return Stream.concat(commonTags.stream(), stream(tags.spliterator(), false))
                .map(t -> new BasicTag(t.getKey(), t.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<io.micrometer.core.instrument.Meter> getMeters() {
        return meterMap.values();
    }

    @Override
    public <M extends io.micrometer.core.instrument.Meter> Optional<M> findMeter(Class<M> mClass, String name, Iterable<io.micrometer.core.instrument.Tag> tags) {
        Collection<io.micrometer.core.instrument.Tag> tagsToMatch = new ArrayList<>();
        tags.forEach(tagsToMatch::add);

        return meterMap.entrySet().stream()
                .filter(e -> mClass.isInstance(e.getValue()))
                .filter(e -> e.getKey().id().name().equals(name))
                .filter(e -> stream(e.getKey().id().tags().spliterator(), false)
                        .map(t -> new ImmutableTag(t.key(), t.value()))
                        .collect(Collectors.toList())
                        .containsAll(tagsToMatch))
                .map(Map.Entry::getValue)
                .findAny()
                .filter(mClass::isInstance)
                .map(mClass::cast);
    }

    public Optional<io.micrometer.core.instrument.Meter> findMeter(io.micrometer.core.instrument.Meter.Type type, String name, Iterable<io.micrometer.core.instrument.Tag> tags) {
        Collection<io.micrometer.core.instrument.Tag> tagsToMatch = new ArrayList<>();
        tags.forEach(tagsToMatch::add);

        return meterMap.entrySet().stream()
                .filter(e -> e.getValue().getType().equals(type))
                .filter(e -> e.getKey().id().name().equals(name))
                .filter(e -> stream(e.getKey().id().tags().spliterator(), false)
                        .map(t -> new ImmutableTag(t.key(), t.value()))
                        .collect(Collectors.toList())
                        .containsAll(tagsToMatch))
                .map(Map.Entry::getValue)
                .findAny();
    }

    @Override
    public io.micrometer.core.instrument.Counter counter(String name, Iterable<io.micrometer.core.instrument.Tag> tags) {
        com.netflix.spectator.api.Counter counter = registry.counter(name, toSpectatorTags(tags));
        return (io.micrometer.core.instrument.Counter) meterMap.computeIfAbsent(counter, c -> new SpectatorCounter(counter));
    }

    @Override
    public io.micrometer.core.instrument.DistributionSummary distributionSummary(String name, Iterable<io.micrometer.core.instrument.Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles);
        com.netflix.spectator.api.DistributionSummary ds = registry.distributionSummary(name, toSpectatorTags(tags));
        return (io.micrometer.core.instrument.DistributionSummary) meterMap.computeIfAbsent(ds, d -> new SpectatorDistributionSummary(ds));
    }

    @Override
    protected io.micrometer.core.instrument.Timer timer(String name, Iterable<io.micrometer.core.instrument.Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles);
        registerHistogramCounterIfNecessary(name, tags, histogram);
        com.netflix.spectator.api.Timer timer = registry.timer(name, toSpectatorTags(tags));
        return (io.micrometer.core.instrument.Timer) meterMap.computeIfAbsent(timer, t -> new SpectatorTimer(timer, getClock()));
    }

    private void registerHistogramCounterIfNecessary(String name, Iterable<io.micrometer.core.instrument.Tag> tags, Histogram<?> histogram) {
        // FIXME need the heisen-counter to complete
//        if(histogram != null) {
//            for (Bucket<?> bucket : histogram.getBuckets()) {
//                List<com.netflix.spectator.api.Tag> histogramTags = new LinkedList<>(toSpectatorTags(tags));
//                histogramTags.add(new BasicTag("bucket", bucket.toString()));
//                histogramTags.add(new BasicTag("statistic", "histogram"));
//                registry.counter(registry.createId(name, histogramTags)).count();
//            }
//        }
    }

    private void registerQuantilesGaugeIfNecessary(String name, Iterable<io.micrometer.core.instrument.Tag> tags, Quantiles quantiles) {
        if(quantiles != null) {
            for (Double q : quantiles.monitored()) {
                List<com.netflix.spectator.api.Tag> quantileTags = new LinkedList<>(toSpectatorTags(tags));
                quantileTags.add(new BasicTag("quantile", Double.isNaN(q) ? "NaN" : Double.toString(q)));
                quantileTags.add(new BasicTag("statistic", "quantile"));
                registry.gauge(registry.createId(name, quantileTags), q, quantiles::get);
            }
        }
    }

    @Override
    public io.micrometer.core.instrument.LongTaskTimer longTaskTimer(String name, Iterable<io.micrometer.core.instrument.Tag> tags) {
        com.netflix.spectator.api.LongTaskTimer timer = registry.longTaskTimer(name, toSpectatorTags(tags));
        return (io.micrometer.core.instrument.LongTaskTimer) meterMap.computeIfAbsent(timer, t -> new SpectatorLongTaskTimer(timer));
    }

    @Override
    public MeterRegistry register(io.micrometer.core.instrument.Meter meter) {
        AbstractMeter<io.micrometer.core.instrument.Meter> spectatorMeter = new AbstractMeter<io.micrometer.core.instrument.Meter>(spectatorClock, spectatorId(registry, meter.getName(), meter.getTags()), meter) {
            @Override
            public Iterable<Measurement> measure() {
                return stream(ref.get().measure().spliterator(), false)
                        .map(m -> new Measurement(spectatorId(registry, m.getName(), m.getTags()), clock.wallTime(), m.getValue()))
                        .collect(Collectors.toList());
            }
        };

        meterMap.put(spectatorMeter, meter);
        return this;
    }

    @Override
    public <T> T gauge(String name, Iterable<io.micrometer.core.instrument.Tag> tags, T obj, ToDoubleFunction<T> f) {
        Id gaugeId = registry.createId(name, toSpectatorTags(tags));
        com.netflix.spectator.api.Gauge gauge = new CustomSpectatorToDoubleGauge<>(registry.clock(), gaugeId, obj, f);
        registry.register(gauge);
        meterMap.computeIfAbsent(gauge, g -> new SpectatorGauge(gauge));
        return obj;
    }

    /**
     * @return The underlying Spectator {@link Registry}.
     */
    public Registry getSpectatorRegistry() {
        return registry.getSpectatorRegistry();
    }
}
