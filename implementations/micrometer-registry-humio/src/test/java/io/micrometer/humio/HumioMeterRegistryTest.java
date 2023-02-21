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
package io.micrometer.humio;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HumioMeterRegistry}.
 *
 * @author Martin Westergaard Lassen
 * @author Jon Schneider
 * @author Johnny Lim
 */
@ExtendWith(WiremockResolver.class)
class HumioMeterRegistryTest {

    private final HumioConfig config = HumioConfig.DEFAULT;

    private final MockClock clock = new MockClock();

    private final HumioMeterRegistry meterRegistry = new HumioMeterRegistry(config, clock);

    @Test
    void writeTimer(@WiremockResolver.Wiremock WireMockServer server) {
        HumioMeterRegistry registry = humioMeterRegistry(server);
        registry.timer("my.timer", "status", "success");

        server.stubFor(any(anyUrl()));
        registry.publish();
        server.verify(postRequestedFor(urlMatching("/api/v1/ingest/humio-structured")).withRequestBody(equalTo(
                "[{\"events\": [{\"timestamp\":\"1970-01-01T00:00:00.001Z\",\"attributes\":{\"name\":\"my_timer\",\"count\":0,\"sum\":0,\"avg\":0,\"max\":0,\"status\":\"success\"}}]}]")));
    }

    @Test
    void datasourceTags(@WiremockResolver.Wiremock WireMockServer server) {
        HumioMeterRegistry registry = humioMeterRegistry(server, "name", "micrometer");
        registry.counter("my.counter").increment();

        server.stubFor(any(anyUrl()));
        registry.publish();
        server.verify(postRequestedFor(urlMatching("/api/v1/ingest/humio-structured"))
            .withRequestBody(containing("\"tags\":{\"name\": \"micrometer\"}")));
    }

    @Test
    void writeGauge() {
        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(createBatch().writeGauge(gauge)).isNotNull();
    }

    private HumioMeterRegistry.Batch createBatch() {
        return meterRegistry.new Batch(clock.wallTime());
    }

    @Test
    void writeGaugeShouldDropNanValue() {
        meterRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(createBatch().writeGauge(gauge)).isNull();
    }

    @Test
    void writeGaugeShouldDropPositiveInfiniteValue() {
        meterRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(createBatch().writeGauge(gauge)).isNull();
    }

    @Test
    void writeGaugeShouldDropNegativeInfiniteValue() {
        meterRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(createBatch().writeGauge(gauge)).isNull();
    }

    @Test
    void writeTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(createBatch().writeGauge(timeGauge)).isNotNull();
    }

    @Test
    void writeTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(createBatch().writeGauge(timeGauge)).isNull();
    }

    @Test
    void writeTimeGaugeShouldDropPositiveInfiniteValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(createBatch().writeGauge(timeGauge)).isNull();
    }

    @Test
    void writeTimeGaugeShouldDropNegativeInfiniteValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(createBatch().writeGauge(timeGauge)).isNull();
    }

    @Test
    void writeFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(meterRegistry);
        clock.add(config.step());
        assertThat(createBatch().writeFunctionCounter(counter)).isNotNull();
    }

    @Test
    void writeFunctionCounterShouldDropPositiveInfiniteValue() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue)
            .register(meterRegistry);
        clock.add(config.step());
        assertThat(createBatch().writeFunctionCounter(counter)).isNull();
    }

    @Test
    void writeFunctionCounterShouldDropNegativeInfiniteValue() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue)
            .register(meterRegistry);
        clock.add(config.step());
        assertThat(createBatch().writeFunctionCounter(counter)).isNull();
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.meterRegistry);
        assertThat(createBatch().writeMeter(meter)).isNull();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement5 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4,
                measurement5);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.meterRegistry);
        assertThat(createBatch().writeMeter(meter)).isEqualTo(
                "{\"timestamp\":\"1970-01-01T00:00:00.001Z\",\"attributes\":{\"name\":\"my_meter\",\"value\":1,\"value\":2}}");
    }

    private HumioMeterRegistry humioMeterRegistry(WireMockServer server, String... tags) {
        return new HumioMeterRegistry(new HumioConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public Map<String, String> tags() {
                Map<String, String> tagMap = new HashMap<>();
                for (int i = 0; i < tags.length; i += 2) {
                    tagMap.put(tags[i], tags[i + 1]);
                }
                return tagMap;
            }
        }, clock);
    }

}
