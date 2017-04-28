package org.springframework.metrics.instrument.spectator;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.metrics.instrument.*;
import org.springframework.metrics.instrument.internal.AbstractMeterRegistry;

import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SpectatorMeterRegistry extends AbstractMeterRegistry {
    private Registry registry;

    public SpectatorMeterRegistry() {
        this(new DefaultRegistry());
    }

    public SpectatorMeterRegistry(Registry registry) {
        this(registry, Clock.SYSTEM);
    }

    @Autowired
    public SpectatorMeterRegistry(Registry registry, Clock clock) {
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

    private Iterable<com.netflix.spectator.api.Tag> toSpectatorTags(Iterable<Tag> tags) {
        return StreamSupport.stream(tags.spliterator(), false)
                .map(t -> new BasicTag(t.getKey(), t.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return register(new SpectatorCounter(registry.counter(name, toSpectatorTags(tags))));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
        return register(new SpectatorDistributionSummary(registry.distributionSummary(name, toSpectatorTags(tags))));
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        return register(new SpectatorTimer(registry.timer(name, toSpectatorTags(tags)), getClock()));
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        return register(new SpectatorLongTaskTimer(registry.longTaskTimer(name, toSpectatorTags(tags))));
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        Id gaugeId = registry.createId(name, toSpectatorTags(tags));
        com.netflix.spectator.api.Gauge gauge = new SpectatorToDoubleGauge<>(registry.clock(), gaugeId, obj, f);
        registry.register(gauge);
        register(new SpectatorGauge(gauge));
        return obj;
    }
}
