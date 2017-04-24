package org.springframework.metrics.collector.spectator;

import com.netflix.spectator.api.*;
import org.springframework.metrics.collector.*;
import org.springframework.metrics.collector.Clock;
import org.springframework.metrics.collector.Counter;
import org.springframework.metrics.collector.DistributionSummary;
import org.springframework.metrics.collector.Tag;
import org.springframework.metrics.collector.Timer;

import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SpectatorMetricCollector extends AbstractMetricCollector {
    private Registry registry;

    public SpectatorMetricCollector() {
        this(new DefaultRegistry());
    }

    public SpectatorMetricCollector(Registry registry) {
        this.registry = registry;
    }

    private Iterable<com.netflix.spectator.api.Tag> toSpectatorTags(Iterable<Tag> tags) {
        return StreamSupport.stream(tags.spliterator(), false)
                .map(t -> new BasicTag(t.getKey(), t.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public Clock getClock() {
        // FIXME how best to inject a clock source and configure a default?
        return System::nanoTime;
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
        return register(new SpectatorTimer(registry.timer(name, toSpectatorTags(tags))));
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
