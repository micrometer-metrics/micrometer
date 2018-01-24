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
package io.micrometer.spring.filter;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import io.micrometer.spring.PropertiesMeterFilter;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import org.junit.Before;
import org.junit.Test;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesMeterFilterTest {
    private MetricsProperties props = new MetricsProperties();

    @Nullable
    private HistogramConfig histogramConfig;

    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock()) {
        @Override
        @NonNull
        protected DistributionSummary newDistributionSummary(@NonNull Meter.Id id, @NonNull HistogramConfig conf) {
            histogramConfig = conf;
            return super.newDistributionSummary(id, conf);
        }
    };

    @Before
    public void before() {
        registry.config().meterFilter(new PropertiesMeterFilter(props));
    }

    @Test
    public void disable() {
        props.getEnabled().put("my.counter", false);
        registry.counter("my.counter");

        assertThat(registry.find("my.counter").counter()).isNull();
    }

    @Test
    public void disableAll() {
        props.getEnabled().put("all", false);
        registry.timer("my.timer");

        assertThat(registry.find("my.timer").timer()).isNull();
    }

    @Test
    public void enable() {
        props.getEnabled().put("all", false);
        props.getEnabled().put("my.timer", true);
        registry.timer("my.timer");

        registry.mustFind("my.timer").timer();
    }

    @Test
    public void summaryHistogramConfig() {
        props.getSummaries().getMaximumExpectedValue().put("my.summary", 100L);
        registry.summary("my.summary");

        assertThat(requireNonNull(histogramConfig).getMaximumExpectedValue()).isEqualTo(100);
    }
}