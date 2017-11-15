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
package io.micrometer.core.instrument.util;

import com.google.common.math.IntMath;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.RoundingMode;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Modified from {@link com.google.common.collect.Lists#partition(List, int)}
 */
public class MeterPartition extends AbstractList<List<Meter>> {
    private final List<Meter> list;
    private final int size;

    public MeterPartition(MeterRegistry registry, int size) {
        this.list = registry.getMeters();
        this.size = size;
    }

    @Override
    public List<Meter> get(int index) {
        int start = index * size;
        int end = Math.min(start + size, list.size());
        return list.subList(start, end);
    }

    @Override
    public int size() {
        return IntMath.divide(list.size(), size, RoundingMode.CEILING);
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    public static List<List<Meter>> partition(MeterRegistry registry, int size) {
        return new MeterPartition(registry, size);
    }
}
