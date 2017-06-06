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
package org.springframework.metrics.instrument.stats.hist;

import java.util.Comparator;
import java.util.Set;
import java.util.function.DoubleFunction;

public class FixedCumulativeBucketFunction<T> implements CumulativeBucketFunction<T> {
    private DoubleFunction<? extends T> f;
    private Set<T> buckets;
    private Comparator<? super T> comp;

    @Override
    public T bucketFloor(double d) {
        return f.apply(d);
    }

    public FixedCumulativeBucketFunction(DoubleFunction<? extends T> f, Set<T> buckets, Comparator<? super T> comp) {
        this.f = f;
        this.buckets = buckets;
        this.comp = comp;
    }

    @Override
    public Set<T> buckets() {
        return buckets;
    }

    @Override
    public Comparator<? super T> bucketComparator() {
        return comp;
    }
}