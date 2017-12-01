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
package io.micrometer.core.instrument.config;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyMeterFilterTest {
    // In a real application, the `get` method would be bound to some property source and
    // mapped to the appropriate type with some sort of type conversion service.
    private PropertyMeterFilter filter = new PropertyMeterFilter() {
        @SuppressWarnings("unchecked")
        @Override
        public <V> V get(String k, Class<V> vClass) {
            if (k.equals("enabled"))
                return (V) (Boolean) false;
            if (k.equals("my.counter.enabled"))
                return (V) (Boolean) false;
            if (k.equals("my.timer.enabled"))
                return (V) (Boolean) true;
            if (k.equals("my.summary.enabled"))
                return (V) (Boolean) true;
            if (k.equals("my.summary.maximumExpectedValue"))
                return (V) (Long) 100L;
            return null;
        }
    };

    private HistogramConfig histogramConfig;

    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock()) {
        @Override
        protected DistributionSummary newDistributionSummary(Meter.Id id, HistogramConfig conf) {
            histogramConfig = conf;
            return super.newDistributionSummary(id, conf);
        }
    };

    @BeforeEach
    void configureRegistry() {
        registry.config().meterFilter(filter);
    }

    @Test
    void disable() {
        registry.counter("my.counter");
        assertThat(registry.find("my.counter").counter()).isNull();
    }

    @Test
    void enable() {
        registry.timer("my.timer");
        registry.mustFind("my.timer").timer();
    }

    @Test
    void summaryHistogramConfig() {
        registry.summary("my.summary");
        assertThat(histogramConfig.getMaximumExpectedValue()).isEqualTo(100);
    }
}
