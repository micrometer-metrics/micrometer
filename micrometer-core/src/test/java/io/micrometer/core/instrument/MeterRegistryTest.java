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

import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;

import static org.assertj.core.api.Assertions.assertThat;

class MeterRegistryTest {
    private MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void acceptMeterFilter() {
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                return id.getName().contains("jvm") ? MeterFilterReply.DENY : MeterFilterReply.NEUTRAL;
            }
        });

        assertThat(registry.counter("jvm.my.counter")).isInstanceOf(NoopCounter.class);
        assertThat(registry.counter("my.counter")).isNotInstanceOf(NoopCounter.class);
    }

    @Test
    void idTransformingMeterFilter() {
        registry.config().meterFilter(MeterFilter.ignoreTags("k1"));

        registry.counter("my.counter", "k1", "v1");
        registry.get("my.counter").counter();
        assertThat(registry.find("my.counter").tags("k1", "v1").counter()).isNull();
    }

    @Test
    void histogramConfigTransformingMeterFilter() {
        MeterRegistry registry = new SimpleMeterRegistry() {
            @Override
            protected Timer newTimer(@Nonnull Meter.Id id, DistributionStatisticConfig histogramConfig, PauseDetector pauseDetector) {
                assertThat(histogramConfig.isPublishingHistogram()).isTrue();
                return super.newTimer(id, histogramConfig, pauseDetector);
            }
        };

        registry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id mappedId, DistributionStatisticConfig config) {
                return DistributionStatisticConfig.builder()
                    .percentiles(0.95)
                    .percentilesHistogram(true)
                    .build()
                    .merge(config);
            }
        });

        registry.timer("my.timer");
    }

    @Test
    void noopMetersAfterRegistryClosed() {
        assertThat(registry.timer("my.timer.before")).isNotInstanceOf(NoopTimer.class);
        registry.close();

        assertThat(registry.isClosed()).isTrue();

        assertThat(registry.timer("my.timer.before")).isNotInstanceOf(NoopTimer.class);
        assertThat(registry.timer("my.timer.after")).isInstanceOf(NoopTimer.class);
    }
}
