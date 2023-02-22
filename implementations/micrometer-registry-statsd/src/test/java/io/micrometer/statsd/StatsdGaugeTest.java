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
package io.micrometer.statsd;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StatsdGaugeTest {

    private AtomicInteger value = new AtomicInteger(1);

    @Test
    void shouldAlwaysPublishValue() {
        AtomicInteger lines = new AtomicInteger();
        MeterRegistry registry = StatsdMeterRegistry.builder(StatsdConfig.DEFAULT)
            .lineSink(l -> lines.incrementAndGet())
            .build();

        StatsdGauge<?> alwaysPublishingGauge = (StatsdGauge<?>) Gauge.builder("test", value, AtomicInteger::get)
            .register(registry);

        alwaysPublishingGauge.poll();
        alwaysPublishingGauge.poll();

        assertThat(lines.get()).isEqualTo(2);
    }

    @Test
    void shouldOnlyPublishValueWhenValueChanges() {
        AtomicInteger lines = new AtomicInteger();
        MeterRegistry registry = StatsdMeterRegistry.builder(new StatsdConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public boolean publishUnchangedMeters() {
                return false;
            }
        }).lineSink(l -> lines.incrementAndGet()).build();

        StatsdGauge<?> gaugePublishingOnChange = (StatsdGauge<?>) Gauge.builder("test", value, AtomicInteger::get)
            .register(registry);

        gaugePublishingOnChange.poll();
        gaugePublishingOnChange.poll();

        assertThat(lines.get()).isEqualTo(1);

        // update value and expect the publisher to be called again
        value.incrementAndGet();
        gaugePublishingOnChange.poll();

        assertThat(lines.get()).isEqualTo(2);
    }

}
