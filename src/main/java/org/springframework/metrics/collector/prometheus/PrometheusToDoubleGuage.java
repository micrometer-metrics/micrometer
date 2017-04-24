package org.springframework.metrics.collector.prometheus;

import io.prometheus.client.SimpleCollector;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * A variation on Prometheus' gauge which maintains a weak reference to an object to track and a double-producing
 * function which measures this object when observed.
 *
 * @param <T> The type of the object that this gauge observes.
 */
public class PrometheusToDoubleGuage<T> extends SimpleCollector<PrometheusToDoubleGuage.Child> {
    private ToDoubleFunction<T> f;

    /** Reference to the underlying object used to compute the measurements. */
    private final WeakReference<T> ref;

    PrometheusToDoubleGuage(SimpleCollector.Builder b, WeakReference<T> ref, ToDoubleFunction<T> f) {
        super(b);
        this.ref = ref;
        this.f = f;
    }

    public static class Builder<T> extends SimpleCollector.Builder<Builder<T>, PrometheusToDoubleGuage> {
        private WeakReference<T> ref;
        private ToDoubleFunction<T> f;

        Builder(T obj, ToDoubleFunction<T> f) {
            this.ref = new WeakReference<>(obj);
            this.f = f;
        }

        @Override
        public PrometheusToDoubleGuage create() {
            return new PrometheusToDoubleGuage<>(this, ref, f);
        }
    }

    public static <T> Builder<T> build(T obj, ToDoubleFunction<T> f) {
        return new Builder<>(obj, f);
    }

    @Override
    protected Child newChild() {
        return new Child();
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples.Sample> samples = new ArrayList<MetricFamilySamples.Sample>();
        for(Map.Entry<List<String>, PrometheusToDoubleGuage.Child> c: children.entrySet()) {
            List<String> tags = c.getKey();
            PrometheusToDoubleGuage<?>.Child gauge = c.getValue();

            samples.add(new MetricFamilySamples.Sample(fullname, labelNames, tags, gauge.value()));
        }
        MetricFamilySamples mfs = new MetricFamilySamples(fullname, Type.GAUGE, help, samples);

        List<MetricFamilySamples> mfsList = new ArrayList<>();
        mfsList.add(mfs);
        return mfsList;
    }

    class Child {
        public double value() {
            final T obj = ref.get();
            return (obj == null) ? Double.NaN : f.applyAsDouble(obj);
        }
    }
}
