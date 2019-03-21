/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.autoconfigure;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Meter;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;

/**
 * A meter value that is used when configuring micrometer. Can be a String representation
 * of either a {@link Long} (applicable to timers and distribution summaries) or a
 * {@link Duration} (applicable to only timers).
 *
 * @author Phillip Webb
 */
final class MeterValue {

    private static final StringToDurationConverter durationConverter = new StringToDurationConverter();

    private final Object value;

    MeterValue(long value) {
        this.value = value;
    }

    MeterValue(Duration value) {
        this.value = value;
    }

    /**
     * Return the underlying value of the SLA in form suitable to apply to the given meter
     * type.
     * @param meterType the meter type
     * @return the value or {@code null} if the value cannot be applied
     */
    public Long getValue(Meter.Type meterType) {
        if (meterType == Meter.Type.DISTRIBUTION_SUMMARY) {
            return getDistributionSummaryValue();
        }
        if (meterType == Meter.Type.TIMER) {
            return getTimerValue();
        }
        return null;
    }

    private Long getDistributionSummaryValue() {
        if (this.value instanceof Long) {
            return (Long) this.value;
        }
        return null;
    }

    private Long getTimerValue() {
        if (this.value instanceof Long) {
            return TimeUnit.MILLISECONDS.toNanos((long) this.value);
        }
        if (this.value instanceof Duration) {
            return ((Duration) this.value).toNanos();
        }
        return null;
    }

    /**
     * Return a new {@link MeterValue} instance for the given String value. The value may
     * contain a simple number, or a {@link Duration duration string}.
     * @param value the source value
     * @return a {@link MeterValue} instance
     */
    public static MeterValue valueOf(String value) {
        if (isNumber(value)) {
            return new MeterValue(Long.parseLong(value));
        }
        return new MeterValue(durationConverter.convert(value));
    }

    /**
     * Return a new {@link MeterValue} instance for the given long value.
     * @param value the source value
     * @return a {@link MeterValue} instance
     */
    public static MeterValue valueOf(long value) {
        return new MeterValue(value);
    }

    private static boolean isNumber(String value) {
        return value.chars().allMatch(Character::isDigit);
    }

}
