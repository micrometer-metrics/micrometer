/**
 * Copyright 2019 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx2;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Tests for {@link Influx2MeterRegistry}.
 *
 * @author Jakub Bednar
 */
@ExtendWith(WiremockResolver.class)
class Influx2MeterRegistryTest {

    private final Influx2Config config = Influx2Config.DEFAULT;
    private final MockClock clock = new MockClock();

    @Test
    void writeGauge(@WiremockResolver.Wiremock WireMockServer server) {
        Influx2MeterRegistry registry = influx2MeterRegistry(server);
        registry.gauge("my.gauge", 1d);

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(equalTo("my_gauge,metric_type=gauge value=1 1"));

        server.verify(matcher);
    }

    @Test
    void writeGaugeShouldDropNanValue(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);
        registry.gauge("my.gauge", Double.NaN);

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(absent());

        server.verify(matcher);
    }

    @Test
    void writeGaugeShouldDropInfiniteValues(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);

        registry.gauge("my.gauge1", Double.POSITIVE_INFINITY);
        registry.gauge("my.gauge2", Double.NEGATIVE_INFINITY);

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(absent());

        server.verify(matcher);
    }

    @Test
    void writeTimeGauge(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);

        AtomicReference<Double> obj = new AtomicReference<>(1d);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(equalTo("my_timeGauge,metric_type=gauge value=1000 1"));

        server.verify(matcher);
    }

    @Test
    void writeTimeGaugeShouldDropNanValue(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);

        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        registry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(absent());

        server.verify(matcher);
    }

    @Test
    void writeTimeGaugeShouldDropInfiniteValues(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);

        AtomicReference<Double> obj1 = new AtomicReference<>(Double.POSITIVE_INFINITY);
        registry.more().timeGauge("my.timeGauge1", Tags.empty(), obj1, TimeUnit.SECONDS, AtomicReference::get);

        AtomicReference<Double> obj2 = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        registry.more().timeGauge("my.timeGauge2", Tags.empty(), obj2, TimeUnit.SECONDS, AtomicReference::get);

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(absent());

        server.verify(matcher);
    }

    @Test
    void writeCounterWithFunction(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);

        FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(registry);
        clock.add(config.step());

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(equalTo("myCounter,metric_type=counter value=1 60001"));

        server.verify(matcher);
    }

    @Test
    void writeCounterWithFunctionCounterShouldDropInfiniteValues(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);

        FunctionCounter.builder("myCounter1", Double.POSITIVE_INFINITY, Number::doubleValue).register(registry);
        FunctionCounter.builder("myCounter2", Double.NEGATIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(config.step());

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(absent());

        server.verify(matcher);
    }

    @Test
    void writeShouldDropTagWithBlankValue(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);

        registry.gauge("my.gauge", Tags.of("foo", "bar").and("baz", ""), 1d);

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(equalTo("my_gauge,foo=bar,metric_type=gauge value=1 1"));

        server.verify(matcher);
    }

    @Test
    void writeCustomMeter(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);

        Measurement m1 = new Measurement(() -> 23d, Statistic.VALUE);
        Measurement m2 = new Measurement(() -> 13d, Statistic.VALUE);
        Measurement m3 = new Measurement(() -> 5d, Statistic.TOTAL_TIME);
        Meter.builder("my.custom", Meter.Type.OTHER, Arrays.asList(m1, m2, m3)).register(registry);

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(equalTo("my_custom,metric_type=other value=23,value=13,total=5 1"));

        server.verify(matcher);
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);

        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);

        Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(absent());

        server.verify(matcher);
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues(@WiremockResolver.Wiremock WireMockServer server) {

        Influx2MeterRegistry registry = influx2MeterRegistry(server);

        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement5 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4, measurement5);

        Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(registry);

        server.stubFor(any(anyUrl()));
        registry.publish();

        RequestPatternBuilder matcher = postRequestedFor(
                urlEqualTo("/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(equalTo("my_meter,metric_type=gauge value=1,value=2 1"));

        server.verify(matcher);
    }

    private Influx2MeterRegistry influx2MeterRegistry(WireMockServer server) {
        return new Influx2MeterRegistry(new Influx2Config() {
            @Override
            public String get(final String key) {
                return null;
            }

            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public String bucket() {
                return "my-bucket";
            }

            @Override
            public String org() {
                return "my-org";
            }

            @Override
            public String token() {
                return "my-token";
            }

            @Override
            public boolean autoCreateBucket() {
                return false;
            }
        }, clock);
    }
}
