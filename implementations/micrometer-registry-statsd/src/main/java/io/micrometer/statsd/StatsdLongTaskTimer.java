package io.micrometer.statsd;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleLongTaskTimer;
import org.reactivestreams.Subscriber;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class StatsdLongTaskTimer extends AbstractMeter implements LongTaskTimer, StatsdPollable {
    private final LongTaskTimer delegate;
    private final StatsdLineBuilder lineBuilder;
    private final Subscriber<String> publisher;

    private final AtomicReference<Long> lastActive = new AtomicReference<>(Long.MIN_VALUE);
    private final AtomicReference<Double> lastDuration = new AtomicReference<>(Double.NEGATIVE_INFINITY);

    StatsdLongTaskTimer(Id id, StatsdLineBuilder lineBuilder, Subscriber<String> publisher, Clock clock) {
        super(id);
        this.delegate = new SimpleLongTaskTimer(id, clock);
        this.lineBuilder = lineBuilder;
        this.publisher = publisher;
    }

    @Override
    public long start() {
        return delegate.start();
    }

    @Override
    public long stop(long task) {
        return delegate.stop(task);
    }

    @Override
    public double duration(long task, TimeUnit unit) {
        return delegate.duration(task, unit);
    }

    @Override
    public double duration(TimeUnit unit) {
        return delegate.duration(unit);
    }

    @Override
    public int activeTasks() {
        return delegate.activeTasks();
    }

    @Override
    public void poll() {
        long active = activeTasks();
        if(lastActive.getAndSet(active) != active) {
            publisher.onNext(lineBuilder.gauge(active, Statistic.ActiveTasks));
        }

        double duration = duration(TimeUnit.NANOSECONDS);
        if(lastDuration.getAndSet(duration) != duration) {
            publisher.onNext(lineBuilder.gauge(duration, Statistic.Duration));
        }
    }
}
