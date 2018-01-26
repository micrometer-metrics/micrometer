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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

class DropwizardMeterRegistryTest {
    private final MockClock clock = new MockClock();
    private final DropwizardMeterRegistry registry = new DropwizardMeterRegistry(
        new DropwizardConfig() {
            @Override
            public String prefix() {
                return "dropwizard";
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }
        }, new MetricRegistry(), HierarchicalNameMapper.DEFAULT, clock);

    @Test
    void gaugeOnNullValue() {
        registry.gauge("gauge", emptyList(), null, obj -> 1.0);
        assertThat(registry.get("gauge").gauge().value()).isEqualTo(Double.NaN);
    }

    @Test
    void customMeasurementsThatDifferOnlyInTagValue() {
        Meter.builder("my.custom", Meter.Type.Gauge, Arrays.asList(
            new Measurement(() -> 1.0, Statistic.Count),
            new Measurement(() -> 2.0, Statistic.Total)
        )).register(registry);
    }
}
