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

import io.micrometer.core.instrument.Meter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MeterValue}.
 *
 * @author Phillip Webb
 */
class MeterValueTest {

    @Test
    void getValueForDistributionSummaryWhenFromLongShouldReturnLongValue() {
        MeterValue meterValue = MeterValue.valueOf(123L);
        assertThat(meterValue.getValue(Meter.Type.DISTRIBUTION_SUMMARY)).isEqualTo(123);
    }

    @Test
    void getValueForDistributionSummaryWhenFromNumberStringShouldReturnLongValue() {
        MeterValue meterValue = MeterValue.valueOf("123");
        assertThat(meterValue.getValue(Meter.Type.DISTRIBUTION_SUMMARY)).isEqualTo(123);
    }

    @Test
    void getValueForDistributionSummaryWhenFromDurationStringShouldReturnNull() {
        MeterValue meterValue = MeterValue.valueOf("123ms");
        assertThat(meterValue.getValue(Meter.Type.DISTRIBUTION_SUMMARY)).isNull();
    }

    @Test
    void getValueForTimerWhenFromLongShouldReturnMsToNanosValue() {
        MeterValue meterValue = MeterValue.valueOf(123L);
        assertThat(meterValue.getValue(Meter.Type.TIMER)).isEqualTo(123000000);
    }

    @Test
    void getValueForTimerWhenFromNumberStringShouldMsToNanosValue() {
        MeterValue meterValue = MeterValue.valueOf("123");
        assertThat(meterValue.getValue(Meter.Type.TIMER)).isEqualTo(123000000);
    }

    @Test
    void getValueForTimerWhenFromDurationStringShouldReturnDurationNanos() {
        MeterValue meterValue = MeterValue.valueOf("123ms");
        assertThat(meterValue.getValue(Meter.Type.TIMER)).isEqualTo(123000000);
    }

    @Test
    void getValueForOthersShouldReturnNull() {
        MeterValue meterValue = MeterValue.valueOf("123");
        assertThat(meterValue.getValue(Meter.Type.COUNTER)).isNull();
        assertThat(meterValue.getValue(Meter.Type.GAUGE)).isNull();
        assertThat(meterValue.getValue(Meter.Type.LONG_TASK_TIMER)).isNull();
        assertThat(meterValue.getValue(Meter.Type.OTHER)).isNull();
    }

}
