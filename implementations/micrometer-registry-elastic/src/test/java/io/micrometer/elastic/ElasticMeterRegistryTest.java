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
import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ElasticMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Alexander Reelsen
 * @author Fabian Koehler
 * @author Johnny Lim
 */
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
        assertThat(ElasticMeterRegistry.TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(1))).contains("1970-01-01T00:00:00.001Z");
    }

    @Test
    void writeTimer() {
        Timer timer = Timer.builder("myTimer").register(registry);
        assertThat(registry.writeTimer(timer)).contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"myTimer\",\"type\":\"timer\",\"count\":0,\"sum\":0.0,\"mean\":0.0,\"max\":0.0}");
    }

    @Test
    void writeCounter() {
        Counter counter = Counter.builder("myCounter").register(registry);
        counter.increment();
        clock.add(config.step());
        assertThat(registry.writeCounter(counter))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:01:00.001Z\",\"name\":\"myCounter\",\"type\":\"counter\",\"count\":1.0}");
    }

    @Test
    void writeFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 123.0, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registry.writeFunctionCounter(counter))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:01:00.001Z\",\"name\":\"myCounter\",\"type\":\"counter\",\"count\":123.0}");
    }

    @Test
    void nanFunctionCounterShouldNotBeWritten() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.NaN, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registry.writeFunctionCounter(counter)).isEmpty();
    }

    @Test
    void writeGauge() {
        Gauge gauge = Gauge.builder("myGauge", 123.0, Number::doubleValue).register(registry);
        assertThat(registry.writeGauge(gauge))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"myGauge\",\"type\":\"gauge\",\"value\":123.0}");
    }

    @Test
    void writeTimeGauge() {
        TimeGauge gauge = TimeGauge.builder("myGauge", 123.0, TimeUnit.MILLISECONDS, Number::doubleValue).register(registry);
        assertThat(registry.writeTimeGauge(gauge))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"myGauge\",\"type\":\"gauge\",\"value\":123.0}");
    }

    @Test
    void writeLongTaskTimer() {
        LongTaskTimer timer = LongTaskTimer.builder("longTaskTimer").register(registry);
        assertThat(registry.writeLongTaskTimer(timer))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"longTaskTimer\",\"type\":\"long_task_timer\",\"activeTasks\":0,\"duration\":0.0}");
    }

    @Test
    void writeSummary() {
        DistributionSummary summary = DistributionSummary.builder("summary").register(registry);
        summary.record(123);
        summary.record(456);
        clock.add(config.step());
        assertThat(registry.writeSummary(summary))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:01:00.001Z\",\"name\":\"summary\",\"type\":\"distribution_summary\",\"count\":2,\"sum\":579.0,\"mean\":289.5,\"max\":456.0}");
    }

    @Test
    void writeMeter() {
        Timer timer = Timer.builder("myTimer").register(registry);
        assertThat(registry.writeMeter(timer))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"myTimer\",\"type\":\"timer\",\"count\":\"0.0\",\"total\":\"0.0\",\"max\":\"0.0\"}");
    }

    @Test
    void writeTags() {
        Counter counter = Counter.builder("myCounter").tag("foo", "bar").tag("spam", "eggs").register(registry);
        counter.increment();
        clock.add(config.step());
        assertThat(registry.writeCounter(counter)).contains("{ \"index\" : {} }\n" +
                "{\"@timestamp\":\"1970-01-01T00:01:00.001Z\",\"name\":\"myCounter\",\"type\":\"counter\",\"foo\":\"bar\",\"spam\":\"eggs\",\"count\":1.0}");
    }

    @Issue("#497")
    @Test
    void nullGauge() {
        Gauge g = Gauge.builder("gauge", null, o -> 1).register(registry);
        assertThat(registry.writeGauge(g)).isNotPresent();

        TimeGauge tg = TimeGauge.builder("time.gauge", null, TimeUnit.MILLISECONDS, o -> 1).register(registry);
        assertThat(registry.writeTimeGauge(tg)).isNotPresent();
    }

    @Issue("#498")
    @Test
    void wholeCountIsReportedWithDecimal() {
        Counter c = Counter.builder("counter").register(registry);
        c.increment(10);
        clock.add(config.step());
        assertThat(registry.writeCounter(c)).contains("{ \"index\" : {} }\n" +
                "{\"@timestamp\":\"1970-01-01T00:01:00.001Z\",\"name\":\"counter\",\"type\":\"counter\",\"count\":10.0}");
    }

    @Issue("#1134")
    @Test
    void infinityGaugeShouldNotBeWritten() {
        Gauge gauge = Gauge.builder("myGauge", Double.NEGATIVE_INFINITY, Number::doubleValue).register(registry);
        assertThat(registry.writeGauge(gauge)).isNotPresent();
    }

    @Issue("#1134")
    @Test
    void infinityTimeGaugeShouldNotBeWritten() {
        TimeGauge gauge = TimeGauge.builder("myGauge", Double.NEGATIVE_INFINITY, TimeUnit.MILLISECONDS, Number::doubleValue).register(registry);
        assertThat(registry.writeTimeGauge(gauge)).isNotPresent();
    }
}
