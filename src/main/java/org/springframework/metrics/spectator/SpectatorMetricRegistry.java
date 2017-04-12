package org.springframework.metrics.spectator;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import org.springframework.metrics.*;

import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SpectatorMetricRegistry implements MetricRegistry {
    private Registry registry;

    public SpectatorMetricRegistry() {
        this(new DefaultRegistry());
    }

    public SpectatorMetricRegistry(Registry registry) {
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
        return new SpectatorCounter(registry.counter(name, toSpectatorTags(tags)));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
        return new SpectatorDistributionSummary(registry.distributionSummary(name, toSpectatorTags(tags)));
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        return new SpectatorTimer(registry.timer(name, toSpectatorTags(tags)));
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        return null;
    }

    @Override
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return null;
    }
}
