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

package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultMeter}.
 *
 * @author Johnny Lim
 */
class DefaultMeterTest {

    @Test
    void equalsAndHashCode() {
        Meter.Type type = Meter.Type.COUNTER;
        Meter.Id id = new Meter.Id("my.meter", Tags.empty(), null, null, type);
        Iterable<Measurement> measurements = Collections.singletonList(new Measurement(() -> 1d, Statistic.COUNT));
        DefaultMeter meter1 = new DefaultMeter(id, type, measurements);
        DefaultMeter meter2 = new DefaultMeter(id, type, measurements);
        assertThat(meter1).isEqualTo(meter2);
        assertThat(meter1).hasSameHashCodeAs(meter2);
    }

}
