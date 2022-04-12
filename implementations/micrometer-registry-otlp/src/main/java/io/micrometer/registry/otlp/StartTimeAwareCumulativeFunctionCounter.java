package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

public class StartTimeAwareCumulativeFunctionCounter<T> extends CumulativeFunctionCounter<T> implements StartTimeAwareMeter {

    final long startTimeNanos;

    public StartTimeAwareCumulativeFunctionCounter(Id id, T obj, ToDoubleFunction<T> f, Clock clock) {
        super(id, obj, f);
        startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
    }

    public long getStartTimeNanos() {
        return this.startTimeNanos;
    }
}
