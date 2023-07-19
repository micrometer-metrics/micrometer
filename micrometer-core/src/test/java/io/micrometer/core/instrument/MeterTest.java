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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Meter}.
 *
 * @author Johnny Lim
 */
class MeterTest {

    TimeGauge gauge = TimeGauge.builder("time.gauge", null, TimeUnit.MILLISECONDS, o -> 1.0)
        .register(new SimpleMeterRegistry());

    @Test
    void matchWhenTimeGaugeShouldUseFunctionForTimeGauge() {
        String matched = gauge.match((gauge) -> "gauge", (counter) -> "counter", (timer) -> "timer",
                (distributionSummary) -> "distributionSummary", (longTaskTimer) -> "longTaskTimer",
                (timeGauge) -> "timeGauge", (functionCounter) -> "functionCounter", (functionTimer) -> "functionTimer",
                (meter) -> "meter");
        assertThat(matched).isEqualTo("timeGauge");
    }

    @Test
    void useWhenTimeGaugeShouldUseConsumerForTimeGauge() {
        StringBuilder used = new StringBuilder();
        gauge.use((gauge) -> used.append("gauge"), (counter) -> used.append("counter"), (timer) -> used.append("timer"),
                (distributionSummary) -> used.append("distributionSummary"),
                (longTaskTimer) -> used.append("longTaskTimer"), (timeGauge) -> used.append("timeGauge"),
                (functionCounter) -> used.append("functionCounter"), (functionTimer) -> used.append("functionTimer"),
                (meter) -> used.append("meter"));
        assertThat(used.toString()).isEqualTo("timeGauge");
    }

}
