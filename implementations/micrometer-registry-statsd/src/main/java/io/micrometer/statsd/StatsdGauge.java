package io.micrometer.statsd;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.util.MeterEquivalence;
import org.reactivestreams.Subscriber;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

public class StatsdGauge<T> extends AbstractMeter implements Gauge, StatsdPollable {
    private final StatsdLineBuilder lineBuilder;
    private final Subscriber<String> publisher;

    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> value;
    private final AtomicReference<Double> lastValue = new AtomicReference<>(Double.NEGATIVE_INFINITY);

    StatsdGauge(Meter.Id id, StatsdLineBuilder lineBuilder, Subscriber<String> publisher, T obj, ToDoubleFunction<T> value) {
        super(id);
        this.lineBuilder = lineBuilder;
        this.publisher = publisher;
        this.ref = new WeakReference<>(obj);
        this.value = value;
    }

    @Override
    public double value() {
        T obj = ref.get();
        return obj != null ? value.applyAsDouble(ref.get()) : 0;
    }

    @Override
    public void poll() {
        double val = value();
        if(lastValue.getAndSet(val) != val) {
            publisher.onNext(lineBuilder.gauge(val));
        }
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }
}
