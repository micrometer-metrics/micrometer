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

/**
 * Internal class for resettable summary statistics
 *
 * @author Georg Pirklbauer
 * @since 1.9.0
 */
final class DynatraceSummary {
    private long count = 0;
    private double total = 0d;
    private double min = 0d;
    private double max = 0d;

    synchronized void recordNonNegative(double amount) {
        if (amount < 0) {
            return;
        }
        if (count == 0) {
            min = amount;
            max = amount;
        } else {
            min = Math.min(min, amount);
            max = Math.max(max, amount);
        }
        count++;
        total += amount;
    }

    public synchronized long getCount() {
        return count;
    }

    public synchronized double getTotal() {
        return total;
    }

    public synchronized double getMin() {
        return min;
    }

    public synchronized double getMax() {
        return max;
    }

    synchronized void reset() {
        min = 0d;
        max = 0d;
        total = 0d;
        count = 0;
    }
}
