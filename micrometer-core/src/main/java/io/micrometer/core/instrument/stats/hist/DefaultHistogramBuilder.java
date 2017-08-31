package io.micrometer.core.instrument.stats.hist;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class DefaultHistogramBuilder<T> implements Histogram.Builder<T> {
    private final BucketFunction<T> f;
    private Histogram.Type type = null;
    private List<BucketListener<T>> bucketListeners = new ArrayList<>();

    DefaultHistogramBuilder(BucketFunction<T> f) {
        this.f = f;
    }

    @Override
    public Histogram<T> create(TimeUnit baseTimeUnit, Histogram.Type defaultType) {
        return new Histogram<>(f, type == null ? defaultType : type, bucketListeners);
    }

    @Override
    public Histogram.Builder bucketListener(BucketListener<T> listener) {
        bucketListeners.add(listener);
        return this;
    }

    @Override
    public Histogram.Builder type(Histogram.Type type) {
        this.type = type;
        return this;
    }
}
