/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.dynatrace.types;

import javax.annotation.concurrent.Immutable;

/**
 * Snapshot of a Dynatrace summary object.
 *
 * @author Georg Pirklbauer
 * @since 1.9.0
 */
@Immutable
public final class DynatraceSummarySnapshot {

    /**
     * For empty value.
     * @since 1.9.18
     */
    public static final DynatraceSummarySnapshot EMPTY = new DynatraceSummarySnapshot(0, 0, 0, 0);

    private final double min;

    private final double max;

    private final double total;

    private final long count;

    DynatraceSummarySnapshot(double min, double max, double total, long count) {
        this.min = min;
        this.max = max;
        this.total = total;
        this.count = count;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getTotal() {
        return total;
    }

    public long getCount() {
        return count;
    }

    @Override
    public String toString() {
        return "DynatraceSummarySnapshot{" + "min=" + min + ", max=" + max + ", total=" + total + ", count=" + count
                + '}';
    }

}
