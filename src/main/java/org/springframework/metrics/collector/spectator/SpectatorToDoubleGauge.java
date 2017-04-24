package org.springframework.metrics.collector.spectator;

import com.netflix.spectator.api.*;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.function.ToDoubleFunction;

/**
 * Gauge that is defined by executing a {@link ToDoubleFunction} on an object.
 * This is identical to com.netflix.spectator.api.ObjectGauge which is not accessible in Spectator.
 */
public class SpectatorToDoubleGauge<T> extends AbstractMeter<T> implements Gauge {

    private final ToDoubleFunction<T> f;

    SpectatorToDoubleGauge(Clock clock, Id id, T obj, ToDoubleFunction<T> f) {
        super(clock, id, obj);
        this.f = f;
    }

    @Override
    public Iterable<Measurement> measure() {
        return Collections.singleton(new Measurement(id, clock.wallTime(), value()));
    }

    @Override
    public double value() {
        final T obj = ref.get();
        return (obj == null) ? Double.NaN : f.applyAsDouble(obj);
    }
}