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
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Measurement;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.spectator.step.FunctionTrackingStepCounter;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.MapAccess;
import io.micrometer.core.instrument.util.MeterId;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;

import static io.micrometer.core.instrument.spectator.SpectatorUtils.spectatorId;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public abstract class SpectatorMeterRegistry extends AbstractMeterRegistry {
    private final TagFormatter tagFormatter;
    private final Registry registry;
    private final ConcurrentMap<com.netflix.spectator.api.Meter, io.micrometer.core.instrument.Meter> meterMap = new ConcurrentHashMap<>();

    public SpectatorMeterRegistry(Registry registry, Clock clock, TagFormatter tagFormatter) {
        super(clock);
        this.registry = registry;
        this.tagFormatter = tagFormatter;
    }

    private Collection<com.netflix.spectator.api.Tag> toSpectatorTags(Iterable<io.micrometer.core.instrument.Tag> tags) {
        return concat(commonTags.stream(), stream(tags.spliterator(), false))
                .map(t -> new BasicTag(tagFormatter.formatTagKey(t.getKey()), tagFormatter.formatTagValue(t.getValue())))
                .collect(toList());
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
                        .collect(toList())
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
                        .collect(toList())
                        .containsAll(tagsToMatch))
                .map(Map.Entry::getValue)
                .findAny();
    }

    @Override
    public io.micrometer.core.instrument.Counter counter(String name, Iterable<io.micrometer.core.instrument.Tag> tags) {
        com.netflix.spectator.api.Counter counter = registry.counter(tagFormatter.formatName(name), toSpectatorTags(tags));
        return MapAccess.computeIfAbsent(meterMap, counter, c -> new SpectatorCounter(counter));
    }

    @Override
    public io.micrometer.core.instrument.DistributionSummary newDistributionSummary(String name, Iterable<io.micrometer.core.instrument.Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles, UnaryOperator.identity());
        com.netflix.spectator.api.DistributionSummary ds = registry.distributionSummary(tagFormatter.formatName(name), toSpectatorTags(tags));
        return (io.micrometer.core.instrument.DistributionSummary) meterMap.computeIfAbsent(ds, d -> new SpectatorDistributionSummary(ds));
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(String name, Iterable<io.micrometer.core.instrument.Tag> tags, Quantiles quantiles, Histogram<?> histogram) {
        // scale nanosecond precise quantile values to seconds
        registerQuantilesGaugeIfNecessary(name, tags, quantiles, t -> t / 1.0e6);

        registerHistogramCounterIfNecessary(name, tags, histogram);
        com.netflix.spectator.api.Timer timer = registry.timer(tagFormatter.formatName(name), toSpectatorTags(tags));
        return MapAccess.computeIfAbsent(meterMap, timer, t -> new SpectatorTimer(timer, quantiles, getClock()));
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
        if(quantiles != null) {
            for (Double q : quantiles.monitored()) {
                List<com.netflix.spectator.api.Tag> quantileTags = new LinkedList<>(toSpectatorTags(tags));
                if(!Double.isNaN(q)) {
                    quantileTags.add(new BasicTag("quantile", Double.toString(q)));
                    quantileTags.add(new BasicTag("statistic", "value"));
                    registry.gauge(registry.createId(tagFormatter.formatName(name), quantileTags), q, q2 -> scaling.apply(quantiles.get(q2)));
                }
            }
        }
    }

    @Override
    public io.micrometer.core.instrument.LongTaskTimer longTaskTimer(String name, Iterable<io.micrometer.core.instrument.Tag> tags) {
        com.netflix.spectator.api.LongTaskTimer timer = registry.longTaskTimer(tagFormatter.formatName(name), toSpectatorTags(tags));
        return MapAccess.computeIfAbsent(meterMap, timer, t -> new SpectatorLongTaskTimer(timer));
    }

    @Override
    public MeterRegistry register(io.micrometer.core.instrument.Meter meter) {
        Id id = spectatorId(registry, meter.getName(), meter.getTags());
        if(registry.get(id) == null) {
            AbstractMeter<io.micrometer.core.instrument.Meter> spectatorMeter = new AbstractMeter<io.micrometer.core.instrument.Meter>(registry.clock(), id, meter) {
                @Override
                public Iterable<Measurement> measure() {
                    io.micrometer.core.instrument.Meter meter = ref.get();
                    if (meter != null) {
                        return meter.measure().stream()
                                .map(m -> {
                                    Iterable<Tag> formattedTags = m.getTags().stream().map(t -> Tag.of(tagFormatter.formatTagKey(t.getKey()), tagFormatter.formatTagValue(t.getValue()))).collect(toList());
                                    return new Measurement(spectatorId(registry, tagFormatter.formatName(m.getName()), formattedTags), clock.wallTime(), m.getValue());
                                })
                                .collect(toList());
                    } else return emptyList();
                }
            };

            meterMap.putIfAbsent(spectatorMeter, meter);
        }
        return this;
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(String name, Iterable<io.micrometer.core.instrument.Tag> tags, T obj, ToDoubleFunction<T> f) {
        Id gaugeId = registry.createId(tagFormatter.formatName(name), toSpectatorTags(tags));
        com.netflix.spectator.api.Gauge gauge = new CustomSpectatorToDoubleGauge<>(registry.clock(), gaugeId, obj, f);
        registry.register(gauge);
        return MapAccess.computeIfAbsent(meterMap, gauge, g -> new SpectatorGauge(gauge));
    }

    /**
     * @return The underlying Spectator {@link Registry}.
     */
    public Registry getSpectatorRegistry() {
        return registry;
    }

    /**
     * Builds a step-interval based counter that is incremented on observation with the difference
     * between the current value of a monotonically increasing function {@code f} and the last observation of {@code f}.
     */
    protected <T> T stepCounter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f, long stepMillis) {
        Id id = spectatorId(this.getSpectatorRegistry(), name, tags);
        FunctionTrackingStepCounter<T> heisenCounter = new FunctionTrackingStepCounter<>(id, getSpectatorRegistry().clock(),
                stepMillis, obj, f);

        meterMap.computeIfAbsent(heisenCounter, c -> {
            registry.register(heisenCounter);
            return new SpectatorMeterWrapper(name, tags, Meter.Type.Counter, c);
        });

        return obj;
    }
}
