/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.cloudwatch;

import com.amazonaws.services.cloudwatch.model.MetricDatum;
import io.micrometer.core.instrument.util.MathUtils;

import java.util.AbstractList;
import java.util.List;

/**
 * Modified from {@link io.micrometer.core.instrument.util.MeterPartition}.
 *
 * @author Dawid Kublik
 */
public class MetricDatumPartition extends AbstractList<List<MetricDatum>> {
    private final List<MetricDatum> list;
    private final int size;

    public MetricDatumPartition(List<MetricDatum> metricData, int size) {
        this.list = metricData;
        this.size = size;
    }

    @Override
    public List<MetricDatum> get(int index) {
        int start = index * size;
        int end = Math.min(start + size, list.size());
        return list.subList(start, end);
    }

    @Override
    public int size() {
        return MathUtils.divideWithCeilingRoundingMode(list.size(), size);
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    public static List<List<MetricDatum>> partition(List<MetricDatum> metricData, int size) {
        return new MetricDatumPartition(metricData, size);
    }
}
