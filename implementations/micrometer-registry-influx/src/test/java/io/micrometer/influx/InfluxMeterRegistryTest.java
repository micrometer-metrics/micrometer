/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.influx;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InfluxMeterRegistry}.
 *
 * @author Tommy Ludwig
 */
class InfluxMeterRegistryTest {

    private final InfluxConfig config = InfluxConfig.DEFAULT;
    private final MockClock clock = new MockClock();
    private final InfluxMeterRegistry meterRegistry = new InfluxMeterRegistry(config, clock);

    @Test
    void writeCustomMeter() {
        String expectedInfluxLine = "my_custom,metric_type=unknown value=23,value=13,total_time=5 1";

        Measurement m1 = new Measurement(() -> 23d, Statistic.VALUE);
        Measurement m2 = new Measurement(() -> 13d, Statistic.VALUE);
        Measurement m3 = new Measurement(() -> 5d, Statistic.TOTAL_TIME);
        Meter meter = Meter.builder("my.custom", Meter.Type.OTHER, Arrays.asList(m1, m2, m3)).register(meterRegistry);

        assertThat(meterRegistry.writeMeter(meter).collect(Collectors.joining())).isEqualTo(expectedInfluxLine);
    }
}
