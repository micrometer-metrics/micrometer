package org.springframework.metrics.instrument.simple;

import org.springframework.metrics.instrument.Gauge;

import java.lang.ref.WeakReference;
import java.util.function.ToDoubleFunction;

public class SimpleGauge<T> implements Gauge {
    private final String name;
    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> value;

    public SimpleGauge(String name, T obj, ToDoubleFunction<T> value) {
        this.name = name;
        this.ref = new WeakReference<>(obj);
        this.value = value;
    }

    @Override
    public double value() {
        return value.applyAsDouble(ref.get());
    }

    @Override
    public String getName() {
        return name;
    }
}
