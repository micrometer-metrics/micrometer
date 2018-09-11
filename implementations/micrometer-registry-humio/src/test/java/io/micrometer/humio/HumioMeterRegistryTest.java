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
package io.micrometer.humio;

import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HumioMeterRegistryTest {
    private MockClock clock = new MockClock();
    private HumioConfig config = new HumioConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String repository() {
            return "fake-repository";
        }

        @Override
        public String apiToken() {
            return "fakeIngestToken";
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private HumioMeterRegistry registry = new HumioMeterRegistry(config, clock);
    private ByteArrayOutputStream bos = new ByteArrayOutputStream();

    @Test
    void timestampFormat() {
        assertThat(HumioMeterRegistry.FORMATTER.format(Instant.ofEpochMilli(1))).isEqualTo("1970-01-01T00:00:00.001Z");
    }

    @Test
    void writeTimer() throws IOException {
        Timer timer = Timer.builder("myTimer").register(registry);
        registry.writeTimer(bos, timer, 0);
        assertThat(bos.toString()).isEqualTo("{\"timestamp\":\"1970-01-01T00:00:00Z\",\"attributes\":{\"name\":\"myTimer\",\"type\":\"timer\",\"count\":0,\"sum\":0.0,\"mean\":0.0,\"max\":0.0}}");
//        JSONAssert.assertEquals(expectedJSONString, actualJSON, strictMode);
    }

    @Test
    void writeCounter() throws Exception {
        Counter counter = Counter.builder("myCounter").register(registry);
        counter.increment();
        registry.writeCounter(bos, counter, 0);
        assertThat(bos.toString()).isEqualTo("{\"timestamp\":\"1970-01-01T00:00:00Z\",\"attributes\":{\"name\":\"myCounter\",\"type\":\"counter\",\"count\":0.0}}");
    }

    @Test
    void writeFunctionCounter() throws Exception {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 123.0, Number::doubleValue).register(registry);
        registry.writeCounter(bos, counter, 0);
        assertThat(bos.toString())
            .isEqualTo("{\"timestamp\":\"1970-01-01T00:00:00Z\",\"attributes\":{\"name\":\"myCounter\",\"type\":\"counter\",\"count\":0.0}}");
    }

    @Test
    void writeGauge() throws Exception {
        Gauge gauge = Gauge.builder("myGauge", 123.0, Number::doubleValue).register(registry);
        registry.writeGauge(bos, gauge, 0);
        assertThat(bos.toString())
            .isEqualTo("{\"timestamp\":\"1970-01-01T00:00:00Z\",\"attributes\":{\"name\":\"myGauge\",\"type\":\"gauge\",\"value\":123.0}}");
    }

    @Test
    void writeTimeGauge() throws Exception {
        TimeGauge gauge = TimeGauge.builder("myGauge", 123.0, TimeUnit.MILLISECONDS, Number::doubleValue).register(registry);
        registry.writeGauge(bos, gauge, 0);
        assertThat(bos.toString())
            .isEqualTo("{\"timestamp\":\"1970-01-01T00:00:00Z\",\"attributes\":{\"name\":\"myGauge\",\"type\":\"gauge\",\"value\":123.0}}");
    }

    @Test
    void writeLongTaskTimer() throws Exception {
        LongTaskTimer timer = LongTaskTimer.builder("longTaskTimer").register(registry);
        registry.writeLongTaskTimer(bos, timer, 0);
        assertThat(bos.toString())
            .isEqualTo("{\"timestamp\":\"1970-01-01T00:00:00Z\",\"attributes\":{\"name\":\"longTaskTimer\",\"type\":\"long_task_timer\",\"activeTasks\":0,\"duration\":0.0}}");
    }

    @Test
    void writeSummary() throws Exception {
        DistributionSummary summary = DistributionSummary.builder("summary").register(registry);
        summary.record(123);
        summary.record(456);
        registry.writeSummary(bos, summary, 0);
        assertThat(bos.toString())
            .isEqualTo("{\"timestamp\":\"1970-01-01T00:00:00Z\",\"attributes\":{\"name\":\"summary\",\"type\":\"distribution_summary\",\"count\":0,\"sum\":0.0,\"mean\":0.0,\"max\":456.0}}");
    }

    @Test
    void writeMeter() throws Exception {
        Timer timer = Timer.builder("myTimer").register(registry);
        registry.writeTimer(bos, timer, 0);
        assertThat(bos.toString()).isEqualTo("{\"timestamp\":\"1970-01-01T00:00:00Z\",\"attributes\":{\"name\":\"myTimer\",\"type\":\"timer\",\"count\":0,\"sum\":0.0,\"mean\":0.0,\"max\":0.0}}");
    }

    @Test
    void writeTags() throws Exception {
        Counter counter = Counter.builder("myCounter").tag("foo", "bar").tag("spam", "eggs").register(registry);
        counter.increment();
        registry.writeCounter(bos, counter, 0);
        assertThat(bos.toString()).isEqualTo("{\"timestamp\":\"1970-01-01T00:00:00Z\",\"attributes\":{\"name\":\"myCounter\",\"type\":\"counter\",\"foo\":\"bar\",\"spam\":\"eggs\",\"count\":0.0}}");
    }

    @Test
    void nullGauge() throws IOException {
        {
            Gauge g = Gauge.builder("gauge", null, o -> 1).register(registry);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            registry.writeGauge(bos, g, 0);
            assertThat(bos.size()).isEqualTo(0);
        }

        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            TimeGauge tg = TimeGauge.builder("time.gauge", null, TimeUnit.MILLISECONDS, o -> 1).register(registry);
            registry.writeGauge(bos, tg, 0);
            assertThat(bos.size()).isEqualTo(0L);
        }
    }

    @Test
    void wholeCountIsReportedWithDecimal() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Counter c = Counter.builder("counter").register(registry);
        c.increment(10);
        registry.writeCounter(bos, c, 0);
        assertThat(bos.toString()).isEqualTo("{\"timestamp\":\"1970-01-01T00:00:00Z\",\"attributes\":{\"name\":\"counter\",\"type\":\"counter\",\"count\":0.0}}");
    }
}