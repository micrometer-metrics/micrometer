package org.springframework.metrics.instrument.simple;

import org.springframework.metrics.instrument.*;
import org.springframework.metrics.instrument.internal.AbstractMeterRegistry;

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
    public Counter counter(String name, Iterable<Tag> tags) {
        return new SimpleCounter(name);
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
        return new SimpleDistributionSummary(name);
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        return new SimpleTimer(name);
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        return new SimpleLongTaskTimer(name, getClock());
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        register(new SimpleGauge<>(name, obj, f));
        return obj;
    }
}
