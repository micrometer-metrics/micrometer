package io.micrometer.core.instrument.stats.hist;

import io.micrometer.core.instrument.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class TimeScalingHistogramBuilder implements Histogram.Builder<Double> {
    private final BucketFunction<Double> f;
    private final TimeUnit fUnits;
    private Histogram.Type type = null;
    private List<BucketListener<Double>> bucketListeners = new ArrayList<>();

    TimeScalingHistogramBuilder(BucketFunction<Double> f, TimeUnit fUnits) {
        this.f = f;
        this.fUnits = fUnits;
    }

    @Override
    public Histogram<Double> create(TimeUnit baseTimeUnit, Histogram.Type defaultType) {
        return new Histogram<>(timeScale(f, baseTimeUnit), type == null ? defaultType : type, bucketListeners);
    }

    @Override
    public Histogram.Builder type(Histogram.Type type) {
        this.type = type;
        return this;
    }

    @Override
    public Histogram.Builder bucketListener(BucketListener<Double> listener) {
        bucketListeners.add(listener);
        return this;
    }

    private BucketFunction<Double> timeScale(BucketFunction<Double> f, TimeUnit baseTimeUnit) {
        return observed -> {
            double unscaledBucket = f.bucket(TimeUtils.convert(observed, baseTimeUnit, fUnits));
            return TimeUtils.convert(unscaledBucket, fUnits, baseTimeUnit);
        };
    }
}
