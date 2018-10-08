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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link Meter}.
 *
 * @author Johnny Lim
 */
class MeterTest {

    @Test
    void matchWhenTimeGaugeShouldUseFunctionForTimeGauge() {
        String matched = Meter.Type.match(
                mock(TimeGauge.class),
                (gauge) -> "gauge",
                (counter) -> "counter",
                (timer) -> "timer",
                (distributionSummary) -> "distributionSummary",
                (longTaskTimer) -> "longTaskTimer",
                (timeGauge) -> "timeGauge",
                (functionCounter) -> "functionCounter",
                (functionTimer) -> "functionTimer",
                (meter) -> "meter");
        assertThat(matched).isEqualTo("timeGauge");
    }

    @Test
    void consumeWhenTimeGaugeShouldUseConsumerForTimeGauge() {
        StringBuilder consumed = new StringBuilder();
        Meter.Type.consume(
                mock(TimeGauge.class),
                (gauge) -> consumed.append("gauge"),
                (counter) -> consumed.append("counter"),
                (timer) -> consumed.append("timer"),
                (distributionSummary) -> consumed.append("distributionSummary"),
                (longTaskTimer) -> consumed.append("longTaskTimer"),
                (timeGauge) -> consumed.append("timeGauge"),
                (functionCounter) -> consumed.append("functionCounter"),
                (functionTimer) -> consumed.append("functionTimer"),
                (meter) -> consumed.append("meter"));
        assertThat(consumed.toString()).isEqualTo("timeGauge");
    }

}
