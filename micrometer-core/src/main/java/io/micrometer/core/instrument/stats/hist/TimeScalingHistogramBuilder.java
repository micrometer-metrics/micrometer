/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    private boolean percentiles = false;

    TimeScalingHistogramBuilder(BucketFunction<Double> f, TimeUnit fUnits) {
        this.f = f;
        this.fUnits = fUnits;
    }

    @Override
    public Histogram<Double> create(TimeUnit baseTimeUnit, Histogram.Type defaultType) {
        return new Histogram<>(timeScale(f, baseTimeUnit), type == null ? defaultType : type, bucketListeners, percentiles);
    }

    @Override
    public Histogram.Builder<Double> type(Histogram.Type type) {
        this.type = type;
        return this;
    }

    @Override
    public Histogram.Builder<Double> bucketListener(BucketListener<Double> listener) {
        bucketListeners.add(listener);
        return this;
    }

    private BucketFunction<Double> timeScale(BucketFunction<Double> f, TimeUnit baseTimeUnit) {
        return observed -> {
            double unscaledBucket = f.bucket(TimeUtils.convert(observed, baseTimeUnit, fUnits));
            return TimeUtils.convert(unscaledBucket, fUnits, baseTimeUnit);
        };
    }

    @Override
    public Histogram.Builder<Double> usedForPercentiles() {
        this.percentiles = true;
        return this;
    }
}
