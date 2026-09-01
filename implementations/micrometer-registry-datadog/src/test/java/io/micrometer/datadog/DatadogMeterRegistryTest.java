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
package io.micrometer.datadog;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import io.micrometer.core.ipc.http.HttpSender;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(WiremockResolver.class)
class DatadogMeterRegistryTest {

    @Issue("#463")
    @Test
    void encodeMetricName(@WiremockResolver.Wiremock WireMockServer server) {
        Clock clock = new MockClock();
        DatadogMeterRegistry registry = new DatadogMeterRegistry(new DatadogConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public String apiKey() {
                return "testApiKey";
            }

            @Override
            public String applicationKey() {
                return "testApplicationKey";
            }

            @Override
            public boolean descriptions() {
                return false;
            }

            @Override
            public boolean enabled() {
                return false;
            }
        }, clock);

        server.stubFor(any(anyUrl()));

        Counter.builder("my.counter#abc")
            .baseUnit(TimeUnit.MICROSECONDS.toString().toLowerCase(Locale.ROOT))
            .description("metric description")
            .register(registry)
            .increment(Math.PI);
        registry.publish();

        server.verify(postRequestedFor(urlEqualTo("/api/v1/series")).withHeader("DD-API-KEY", equalTo("testApiKey"))
            .withoutHeader("DD-APPLICATION-KEY")
            .withRequestBody(equalToJson(
                    "{\"series\":[{\"metric\":\"my.counter#abc\",\"points\":[[0,0.0]],\"type\":\"count\",\"unit\":\"microsecond\",\"tags\":[\"statistic:count\"]}]}")));

        registry.close();
    }

    @Test
    void testWithDescriptionEnabled(@WiremockResolver.Wiremock WireMockServer server) {
        Clock clock = new MockClock();
        DatadogMeterRegistry registry = new DatadogMeterRegistry(new DatadogConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public String apiKey() {
                return "testApiKey";
            }

            @Override
            public String applicationKey() {
                return "testApplicationKey";
            }

            @Override
            public boolean descriptions() {
                return true;
            }

            @Override
            public boolean enabled() {
                return false;
            }
        }, clock);

        server.stubFor(any(anyUrl()));

        Counter.builder("my.counter#abc")
            .baseUnit(TimeUnit.MICROSECONDS.toString().toLowerCase(Locale.ROOT))
            .description("metric description")
            .register(registry)
            .increment(Math.PI);
        registry.publish();

        server.verify(postRequestedFor(urlEqualTo("/api/v1/series")).withHeader("DD-API-KEY", equalTo("testApiKey"))
            .withoutHeader("DD-APPLICATION-KEY")
            .withRequestBody(equalToJson(
                    "{\"series\":[{\"metric\":\"my.counter#abc\",\"points\":[[0,0.0]],\"type\":\"count\",\"unit\":\"microsecond\",\"tags\":[\"statistic:count\"]}]}")));

        server.verify(putRequestedFor(urlEqualTo("/api/v1/metrics/my.counter%23abc"))
            .withHeader("DD-API-KEY", equalTo("testApiKey"))
            .withHeader("DD-APPLICATION-KEY", equalTo("testApplicationKey"))
            .withRequestBody(equalToJson(
                    "{\"type\":\"count\",\"unit\":\"microsecond\",\"description\":\"metric description\"}")));

        registry.close();
    }

    @Test
    void doNotPublishMetricMetadataWhenDescriptionIsEnabledButNull() {
        DatadogConfig config = new DatadogConfig() {

            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public String apiKey() {
                return "testApiKey";
            }

            @Override
            public String applicationKey() {
                return "testApplicationKey";
            }
        };

        HttpSender httpSender = mock(HttpSender.class);
        DatadogMeterRegistry registry = DatadogMeterRegistry.builder(config).httpClient(httpSender).build();
        Meter.Id id = new Meter.Id("my.meter", Tags.empty(), null, null, Meter.Type.COUNTER);
        registry.postMetricMetadata("my.meter", new DatadogMetricMetadata(id, Statistic.COUNT, true, null));
        verifyNoInteractions(httpSender);
    }

    @Test
    void doNotPublishMetricMetadataWhenApplicationKeyIsNull() {
        DatadogConfig config = new DatadogConfig() {
            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public String apiKey() {
                return "testApiKey";
            }

            @Override
            public boolean descriptions() {
                return true;
            }

            @Override
            public boolean enabled() {
                return false;
            }
        };
        HttpSender httpSender = mock(HttpSender.class);
        DatadogMeterRegistry registry = DatadogMeterRegistry.builder(config).httpClient(httpSender).build();

        Meter.Id id = new Meter.Id("test.meter", Tags.empty(), null, "test", Meter.Type.COUNTER);
        registry.postMetricMetadata("test.meter", new DatadogMetricMetadata(id, Statistic.COUNT, true, null));
        assertThat(config.applicationKey()).isNull();
        verifyNoInteractions(httpSender);
    }

    @Test
    void publishCompressesMetricsWhenEnabled(@WiremockResolver.Wiremock WireMockServer server) {
        Clock clock = new MockClock();
        DatadogMeterRegistry registry = new DatadogMeterRegistry(new DatadogConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public String apiKey() {
                return "testApiKey";
            }

            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public boolean compress() {
                return true;
            }
        }, clock);

        server.stubFor(any(anyUrl()));

        Counter.builder("my.counter#abc")
            .baseUnit(TimeUnit.MICROSECONDS.toString().toLowerCase(Locale.ROOT))
            .register(registry)
            .increment(Math.PI);
        registry.publish();

        server.verify(postRequestedFor(urlEqualTo("/api/v1/series")).withHeader("DD-API-KEY", equalTo("testApiKey"))
            .withoutHeader("DD-APPLICATION-KEY")
            .withHeader("Content-Encoding", equalTo("gzip")));

        registry.close();
    }

    @Test
    void publishDoesNotCompressMetricsWhenDisabled(@WiremockResolver.Wiremock WireMockServer server) {
        Clock clock = new MockClock();
        DatadogMeterRegistry registry = new DatadogMeterRegistry(new DatadogConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public String apiKey() {
                return "testApiKey";
            }

            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public boolean compress() {
                return false;
            }
        }, clock);

        server.stubFor(any(anyUrl()));

        Counter.builder("my.counter#abc")
            .baseUnit(TimeUnit.MICROSECONDS.toString().toLowerCase(Locale.ROOT))
            .register(registry)
            .increment(Math.PI);
        registry.publish();

        server.verify(postRequestedFor(urlEqualTo("/api/v1/series")).withHeader("DD-API-KEY", equalTo("testApiKey"))
            .withoutHeader("DD-APPLICATION-KEY")
            .withoutHeader("Content-Encoding"));

        registry.close();
    }

    @Test
    void postMetricMetadataDoesNotCompressWhenCompressionEnabled() throws Throwable {
        DatadogConfig config = new DatadogConfig() {

            @Override
            public @Nullable String get(String key) {
                return null;
            }

            @Override
            public String apiKey() {
                return "testApiKey";
            }

            @Override
            public String applicationKey() {
                return "testApplicationKey";
            }

            @Override
            public boolean compress() {
                return true;
            }
        };

        HttpSender httpSender = mock(HttpSender.class);
        when(httpSender.put(anyString())).thenCallRealMethod();
        when(httpSender.newRequest(anyString())).thenCallRealMethod();
        when(httpSender.send(org.mockito.ArgumentMatchers.any(HttpSender.Request.class)))
            .thenReturn(new HttpSender.Response(200, ""));
        DatadogMeterRegistry registry = DatadogMeterRegistry.builder(config).httpClient(httpSender).build();
        Meter.Id id = new Meter.Id("my.meter", Tags.empty(), "metric description", "milliseconds", Meter.Type.COUNTER);
        registry.postMetricMetadata("my.meter", new DatadogMetricMetadata(id, Statistic.COUNT, true, null));

        verify(httpSender).send(argThat(request -> !request.getRequestHeaders().containsKey("Content-Encoding")));

        registry.close();
    }

}
