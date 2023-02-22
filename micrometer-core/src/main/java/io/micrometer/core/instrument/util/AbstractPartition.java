/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.util;

import java.util.AbstractList;
import java.util.List;

/**
 * Base class for a partition.
 *
 * <p>
 * Those extending this should pass control of the input to this type and never mutate it
 * nor call any mutation operations on this type.
 *
 * @author Jon Schneider
 * @since 1.2.2
 */
public abstract class AbstractPartition<T> extends AbstractList<List<T>> {

    final List<T> delegate;

    final int partitionSize;

    final int partitionCount;

    protected AbstractPartition(List<T> delegate, int partitionSize) {
        if (delegate == null)
            throw new NullPointerException("delegate == null");
        this.delegate = delegate;
        if (partitionSize < 1)
            throw new IllegalArgumentException("partitionSize < 1");
        this.partitionSize = partitionSize;
        this.partitionCount = partitionCount(delegate, partitionSize);
    }

    @Override
    public List<T> get(int index) {
        int start = index * partitionSize;
        int end = Math.min(start + partitionSize, delegate.size());
        return delegate.subList(start, end);
    }

    @Override
    public int size() {
        return this.partitionCount;
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    // This rounds up on remainder to avoid orphaning.
    static <T> int partitionCount(List<T> delegate, int partitionSize) {
        int result = delegate.size() / partitionSize;
        return delegate.size() % partitionSize == 0 ? result : result + 1;
    }

}
