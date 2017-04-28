package org.springframework.metrics.instrument.spectator;

import com.netflix.spectator.api.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Allows for overriding the clock on a Spectator Registry.
 */
public class ExternalClockSpectatorRegistry implements Registry {
    private final Registry composite;
    private final Clock clock;

    ExternalClockSpectatorRegistry(Registry registry, Clock clock) {
        this.composite = registry;
        this.clock = clock;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public RegistryConfig config() {
        return composite.config();
    }

    @Override
    public Id createId(String name) {
        return composite.createId(name);
    }

    @Override
    public Id createId(String name, Iterable<Tag> tags) {
        return composite.createId(name, tags);
    }

    @Override
    public void register(Meter meter) {
        composite.register(meter);
    }

    @Override
    public ConcurrentMap<Id, Object> state() {
        return composite.state();
    }

    @Override
    public Counter counter(Id id) {
        return composite.counter(id);
    }

    @Override
    public DistributionSummary distributionSummary(Id id) {
        return composite.distributionSummary(id);
    }

    @Override
    public Timer timer(Id id) {
        return composite.timer(id);
    }

    @Override
    public Gauge gauge(Id id) {
        return composite.gauge(id);
    }

    @Override
    public Meter get(Id id) {
        return composite.get(id);
    }

    @Override
    public Iterator<Meter> iterator() {
        return composite.iterator();
    }

    @Override
    public <T extends Registry> T underlying(Class<T> c) {
        return composite.underlying(c);
    }

    @Override
    public Id createId(String name, String... tags) {
        return composite.createId(name, tags);
    }

    @Override
    public Id createId(String name, Map<String, String> tags) {
        return composite.createId(name, tags);
    }

    @Override
    public Counter counter(String name) {
        return composite.counter(name);
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return composite.counter(name, tags);
    }

    @Override
    public Counter counter(String name, String... tags) {
        return composite.counter(name, tags);
    }

    @Override
    public DistributionSummary distributionSummary(String name) {
        return composite.distributionSummary(name);
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
        return composite.distributionSummary(name, tags);
    }

    @Override
    public DistributionSummary distributionSummary(String name, String... tags) {
        return composite.distributionSummary(name, tags);
    }

    @Override
    public Timer timer(String name) {
        return composite.timer(name);
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        return composite.timer(name, tags);
    }

    @Override
    public Timer timer(String name, String... tags) {
        return composite.timer(name, tags);
    }

    @Override
    public LongTaskTimer longTaskTimer(Id id) {
        return com.netflix.spectator.api.patterns.LongTaskTimer.get(this, id);
    }

    @Override
    public LongTaskTimer longTaskTimer(String name) {
        return longTaskTimer(createId(name));
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        return longTaskTimer(createId(name, tags));
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, String... tags) {
        return longTaskTimer(createId(name, toIterable(tags)));
    }

    @Override
    public <T extends Number> T gauge(Id id, T number) {
        return composite.gauge(id, number);
    }

    @Override
    public <T extends Number> T gauge(String name, T number) {
        return composite.gauge(name, number);
    }

    @Override
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return composite.gauge(name, tags, number);
    }

    @Override
    public <T> T gauge(Id id, T obj, ToDoubleFunction<T> f) {
        return composite.gauge(id, obj, f);
    }

    @Override
    public <T> T gauge(String name, T obj, ToDoubleFunction<T> f) {
        return composite.gauge(name, obj, f);
    }

    @Override
    public <T extends Collection<?>> T collectionSize(Id id, T collection) {
        return composite.collectionSize(id, collection);
    }

    @Override
    public <T extends Collection<?>> T collectionSize(String name, T collection) {
        return composite.collectionSize(name, collection);
    }

    @Override
    public <T extends Map<?, ?>> T mapSize(Id id, T collection) {
        return composite.mapSize(id, collection);
    }

    @Override
    public <T extends Map<?, ?>> T mapSize(String name, T collection) {
        return composite.mapSize(name, collection);
    }

    @Override
    public void methodValue(Id id, Object obj, String method) {
        composite.methodValue(id, obj, method);
    }

    @Override
    public void methodValue(String name, Object obj, String method) {
        composite.methodValue(name, obj, method);
    }

    @Override
    public Stream<Meter> stream() {
        return composite.stream();
    }

    @Override
    public Stream<Counter> counters() {
        return composite.counters();
    }

    @Override
    public Stream<DistributionSummary> distributionSummaries() {
        return composite.distributionSummaries();
    }

    @Override
    public Stream<Timer> timers() {
        return composite.timers();
    }

    @Override
    public Stream<Gauge> gauges() {
        return composite.gauges();
    }

    @Override
    public void propagate(String msg, Throwable t) {
        composite.propagate(msg, t);
    }

    @Override
    public void propagate(Throwable t) {
        composite.propagate(t);
    }

    private static Iterable<Tag> toIterable(String[] tags) {
        if (tags.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }
        ArrayList<Tag> ts = new ArrayList<>(tags.length);
        for (int i = 0; i < tags.length; i += 2) {
            ts.add(new BasicTag(tags[i], tags[i + 1]));
        }
        return ts;
    }
}
