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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class DefaultHistogramBuilder<T> implements Histogram.Builder<T> {
    private final BucketFunction<T> f;
    private Histogram.Type type = null;
    private List<BucketListener<T>> bucketListeners = new ArrayList<>();
    private boolean percentiles = false;

    DefaultHistogramBuilder(BucketFunction<T> f) {
        this.f = f;
    }

    @Override
    public Histogram<T> create(TimeUnit baseTimeUnit, Histogram.Type defaultType) {
        return new Histogram<>(f, type == null ? defaultType : type, bucketListeners, percentiles);
    }

    @Override
    public Histogram.Builder<T> bucketListener(BucketListener<T> listener) {
        bucketListeners.add(listener);
        return this;
    }

    @Override
    public Histogram.Builder<T> type(Histogram.Type type) {
        this.type = type;
        return this;
    }

    @Override
    public Histogram.Builder<T> usedForPercentiles() {
        this.percentiles = true;
        return this;
    }
}
