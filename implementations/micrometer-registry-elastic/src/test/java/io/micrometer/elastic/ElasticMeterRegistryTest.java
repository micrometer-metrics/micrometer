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
package io.micrometer.elastic;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.TimeGauge;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticMeterRegistryTest {
    private MockClock clock = new MockClock();
    private ElasticConfig config = new ElasticConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private ElasticMeterRegistry registry = new ElasticMeterRegistry(config, clock);

    @Test
    void timestampFormat() {
        assertThat(ElasticMeterRegistry.IndexBuilder.timestamp(1)).isEqualTo("1970-01-01T00:00:00.001Z");
    }

    @Test
    void buildIndex() {
        assertThat(registry.index("myTimer", "timer", 0)
                .field("phi", "0.99").field("value", 1).build())
                .isEqualTo("{\"index\":{\"_index\":\"metrics-1970-01\",\"_type\":\"doc\"}}\n" +
                        "{\"@timestamp\":\"1970-01-01T00:00:00Z\",\"name\":\"myTimer\",\"type\":\"timer\",\"phi\":\"0.99\",\"value\":1.0}");
    }

    @Issue("#497")
    @Test
    void nullGauge() {
        Gauge g = Gauge.builder("gauge", null, o -> 1).register(registry);
        assertThat(registry.writeGauge(g, 0)).isEmpty();

        TimeGauge tg = TimeGauge.builder("time.gauge", null, TimeUnit.MILLISECONDS, o -> 1).register(registry);
        assertThat(registry.writeGauge(tg, 0)).isEmpty();
    }

    @Issue("#498")
    @Test
    void wholeCountIsReportedWithDecimal() {
        Counter c = Counter.builder("counter").register(registry);
        c.increment(10);
        assertThat(registry.writeCounter(c, 0)).containsExactly("{\"index\":{\"_index\":\"metrics-1970-01\",\"_type\":\"doc\"}}\n" +
                "{\"@timestamp\":\"1970-01-01T00:00:00Z\",\"name\":\"counter\",\"type\":\"counter\",\"count\":0.0}");
    }
}