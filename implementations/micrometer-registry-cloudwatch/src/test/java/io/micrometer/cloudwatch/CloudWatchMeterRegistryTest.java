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
package io.micrometer.cloudwatch;

import com.amazonaws.services.cloudwatch.model.MetricDatum;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests for {@link CloudWatchMeterRegistry}.
 *
 * @author Johnny Lim
 */
class CloudWatchMeterRegistryTest {
    private final CloudWatchConfig config = new CloudWatchConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String namespace() {
            return "namespace";
        }
    };

    private CloudWatchMeterRegistry registry = new CloudWatchMeterRegistry(config, new MockClock(), null);

    @Test
    void metricData() {
        registry.gauge("gauge", 1d);
        List<MetricDatum> metricDatumStream = registry.metricData();
        assertThat(metricDatumStream.size()).isEqualTo(1);
    }

    @Test
    void metricDataWhenNaNShouldNotAdd() {
        registry.gauge("gauge", Double.NaN);

        AtomicReference<Double> value = new AtomicReference<>(Double.NaN);
        registry.more().timeGauge("time.gauge", Tags.empty(), value, TimeUnit.MILLISECONDS, AtomicReference::get);

        List<MetricDatum> metricDatumStream = registry.metricData();
        assertThat(metricDatumStream.size()).isEqualTo(0);
    }
}
