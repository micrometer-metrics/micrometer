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
package io.micrometer.core.instrument;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.util.TimeUtils;

public final class CountAtValue {

    public static CountAtValue of(long value, double count) {
        return new CountAtValue(value, count);
    }

    private final long value;
    private final double count;

    private CountAtValue(long value, double count) {
        this.value = value;
        this.count = count;
    }

    public long value() {
        return value;
    }

    public double value(TimeUnit unit) {
        return TimeUtils.nanosToUnit(value, unit);
    }

    public double count() {
        return count;
    }

    @Override
    public String toString() {
        return "(" + count + " at " + value + ')';
    }
}
