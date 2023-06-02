/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents.hc5;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientMetricsBinder.DEFAULT_METER_NAME;
import static io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientMetricsBinder.builder;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Unit tests for {@link ApacheHttpClientMetricsBinder}.
 *
 * @author Benjamin Hubert (benjamin.hubert@willhaben.at)
 * @author Lars Uffmann
 */
@ExtendWith(WiremockResolver.class)
class ApacheHttpClientMetricsBinderTest {

    private static final InternalLogger logger = InternalLoggerFactory
        .getInstance(ApacheHttpClientMetricsBinderTest.class);

    private static final HttpClientResponseHandler<ClassicHttpResponse> NOOP_RESPONSE_HANDLER = (response) -> response;

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @ParameterizedTest
    @MethodSource("commonArgs")
    void timeSuccessful(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(any(anyUrl()));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl()).build();
                execute(client, request);
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl()));
            }
        }

        Timer timer = registry.get(DEFAULT_METER_NAME).timer();
        logStats("timeSuccessful", timer);
        assertThat(timer.count()).isEqualTo(1L);
    }

    private static Stream<Arguments> commonArgs() {
        // @formatter:off
        return Stream.of(
            // 1. meterRegistry, classic
            arguments(false, false),
            // 2. observationRegistry, classic
            arguments(true, false),
            // 3. meterRegistry, async
            arguments(false, true),
            // 4. observationRegistry, async
            arguments(true, true)
        );
        // @formatter:on
    }

    private void execute(CloseableHttpAsyncClient client, SimpleHttpRequest request)
            throws IOException, ExecutionException, InterruptedException {
        client.start();
        Future<SimpleHttpResponse> responseFuture = client.execute(request, null);
        responseFuture.get();
    }

    private void execute(CloseableHttpClient client, ClassicHttpRequest request) throws IOException {
        EntityUtils.consume(client.execute(request, NOOP_RESPONSE_HANDLER).getEntity());
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void httpMethodIsTagged(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(any(anyUrl()));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                execute(client, SimpleRequestBuilder.get(server.baseUrl()).build());
                execute(client, SimpleRequestBuilder.get(server.baseUrl()).build());
                execute(client, SimpleRequestBuilder.post(server.baseUrl()).build());
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl()));
                execute(client, new HttpGet(server.baseUrl()));
                execute(client, new HttpPost(server.baseUrl()));
            }
        }

        assertThat(registry.get(DEFAULT_METER_NAME).tag("method", "GET").tag("exception", "None").timer().count())
            .isEqualTo(2L);
        assertThat(registry.get(DEFAULT_METER_NAME).tags("method", "POST").timer().count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void httpStatusCodeIsTagged(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(any(urlEqualTo("/ok")).willReturn(aResponse().withStatus(200)));
        server.stubFor(any(urlEqualTo("/notfound")).willReturn(aResponse().withStatus(404)));
        server.stubFor(any(urlEqualTo("/error")).willReturn(aResponse().withStatus(500)));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/ok").build());
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/ok").build());
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/notfound").build());
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/error").build());
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl() + "/ok"));
                execute(client, new HttpGet(server.baseUrl() + "/ok"));
                execute(client, new HttpGet(server.baseUrl() + "/notfound"));
                execute(client, new HttpGet(server.baseUrl() + "/error"));
            }
        }
        assertThat(registry.get(DEFAULT_METER_NAME)
            .tags("method", "GET", "status", "200", "outcome", "SUCCESS")
            .tag("exception", "None")
            .timer()
            .count()).isEqualTo(2L);
        assertThat(registry.get(DEFAULT_METER_NAME)
            .tags("method", "GET", "status", "404", "outcome", "CLIENT_ERROR")
            .tag("exception", "None")
            .timer()
            .count()).isEqualTo(1L);
        assertThat(registry.get(DEFAULT_METER_NAME)
            .tags("method", "GET", "status", "500", "outcome", "SERVER_ERROR")
            .tag("exception", "None")
            .timer()
            .count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void uriIsUnknownByDefault(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(any(anyUrl()));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                execute(client, SimpleRequestBuilder.get(server.baseUrl()).build());
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/someuri").build());
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/otheruri").build());
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl()));
                execute(client, new HttpGet(server.baseUrl() + "/someuri"));
                execute(client, new HttpGet(server.baseUrl() + "/otheruri"));
            }
        }
        assertThat(registry.get(DEFAULT_METER_NAME).tags("uri", "UNKNOWN").timer().count()).isEqualTo(3L);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void uriIsReadFromHttpHeader(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(any(anyUrl()));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                execute(client,
                        SimpleRequestBuilder.get(server.baseUrl())
                            .addHeader(DefaultUriMapper.URI_PATTERN_HEADER, "/some/pattern")
                            .build());
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                HttpGet getWithHeader = new HttpGet(server.baseUrl());
                getWithHeader.addHeader(DefaultUriMapper.URI_PATTERN_HEADER, "/some/pattern");
                execute(client, getWithHeader);
            }
        }
        assertThat(registry.get(DEFAULT_METER_NAME).tags("uri", "/some/pattern").timer().count()).isEqualTo(1L);
        assertThatCode(() -> registry.get(DEFAULT_METER_NAME).tags("uri", "UNKNOWN").timer())
            .isInstanceOf(MeterNotFoundException.class);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void routeNotTaggedByDefault(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(any(anyUrl()));
        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/ok").build());
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl()));
            }
        }
        List<String> tagKeys = registry.get(DEFAULT_METER_NAME)
            .timer()
            .getId()
            .getTags()
            .stream()
            .map(Tag::getKey)
            .collect(Collectors.toList());
        assertThat(tagKeys).doesNotContain("target.scheme", "target.host", "target.port").contains("status", "method");
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void routeTaggedIfEnabled(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(any(anyUrl()));
        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .exportTagsForRoute(true)
            .build();
        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder, 1)) {
                execute(client, SimpleRequestBuilder.get(server.baseUrl()).build());
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl()));
            }
        }
        List<String> tagKeys = registry.get(DEFAULT_METER_NAME)
            .timer()
            .getId()
            .getTags()
            .stream()
            .map(Tag::getKey)
            .collect(Collectors.toList());
        assertThat(tagKeys).contains("target.scheme", "target.host", "target.port");
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void uriMapperWorksAsExpected(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(any(anyUrl()));
        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .uriMapper(HttpRequest::getRequestUri)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder, 1)) {
                execute(client, SimpleRequestBuilder.get(server.baseUrl()).build());
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/foo").build());
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/bar").build());
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/foo").build());
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl()));
                execute(client, new HttpGet(server.baseUrl() + "/foo"));
                execute(client, new HttpGet(server.baseUrl() + "/bar"));
                execute(client, new HttpGet(server.baseUrl() + "/foo"));
            }
        }
        assertThat(registry.get(DEFAULT_METER_NAME).tags("uri", "/").timer().count()).isEqualTo(1L);
        assertThat(registry.get(DEFAULT_METER_NAME).tags("uri", "/foo").timer().count()).isEqualTo(2L);
        assertThat(registry.get(DEFAULT_METER_NAME).tags("uri", "/bar").timer().count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void additionalTagsAreExposed(boolean async, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(any(anyUrl()));
        ApacheHttpClientMetricsBinder metricsBinder = builder(registry).tags(Tags.of("foo", "bar", "some.key", "value"))
            .exportTagsForRoute(true)
            .build();
        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder, 1)) {
                execute(client, SimpleRequestBuilder.get(server.baseUrl()).build());
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl()));
            }
        }
        assertThat(registry.get(DEFAULT_METER_NAME)
            .tags("foo", "bar", "some.key", "value", "target.host", "localhost")
            .timer()
            .count()).isEqualTo(1L);
    }

    @Test
    void settingNullRegistryThrowsException() {
        assertThatCode(() -> builder(null).build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void overridingUriMapperWithNullThrowsException() {
        assertThatCode(() -> builder(registry).uriMapper(null).build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void overrideExtraTagsDoesNotThrowAnException(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        ApacheHttpClientMetricsBinder executor = builder(registry).tags(null).build();
        CloseableHttpClient client = client(executor);
        execute(client, new HttpGet(server.baseUrl()));
        assertThat(registry.get(DEFAULT_METER_NAME)).isNotNull();
    }

    @Test
    void globalConventionUsedWhenCustomConventionNotConfigured(@WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        ObservationRegistry observationRegistry = createObservationRegistry();
        observationRegistry.observationConfig().observationConvention(new CustomGlobalApacheHttpConvention());
        ApacheHttpClientMetricsBinder micrometerHttpRequestExecutor = builder(registry)
            .observationRegistry(observationRegistry)
            .build();
        CloseableHttpClient client = client(micrometerHttpRequestExecutor);
        execute(client, new HttpGet(server.baseUrl()));
        assertThat(registry.get("custom.apache.http.client.requests")).isNotNull();
    }

    @Test
    void localConventionTakesPrecedentOverGlobalConvention(@WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        ObservationRegistry observationRegistry = createObservationRegistry();
        observationRegistry.observationConfig().observationConvention(new CustomGlobalApacheHttpConvention());
        ApacheHttpClientMetricsBinder micrometerHttpRequestExecutor = builder(registry)
            .observationRegistry(observationRegistry)
            .observationConvention(new CustomGlobalApacheHttpConvention() {
                @Override
                public String getName() {
                    return "local." + super.getName();
                }
            })
            .build();
        CloseableHttpClient client = client(micrometerHttpRequestExecutor);
        execute(client, new HttpGet(server.baseUrl()));
        assertThat(registry.get("local.custom.apache.http.client.requests")).isNotNull();
    }

    @Test
    void localConventionConfigured(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        ObservationRegistry observationRegistry = createObservationRegistry();
        ApacheHttpClientMetricsBinder micrometerHttpRequestExecutor = builder(registry)
            .observationRegistry(observationRegistry)
            .observationConvention(new CustomGlobalApacheHttpConvention())
            .build();
        CloseableHttpClient client = client(micrometerHttpRequestExecutor);
        execute(client, new HttpGet(server.baseUrl()));
        assertThat(registry.get("custom.apache.http.client.requests")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "get", "post", "custom" })
    void contextualNameContainsRequestMethod(String method, @WiremockResolver.Wiremock WireMockServer server)
            throws IOException {
        server.stubFor(any(anyUrl()));
        TestObservationRegistry observationRegistry = TestObservationRegistry.create();
        ApacheHttpClientMetricsBinder micrometerHttpRequestExecutor = builder(registry)
            .observationRegistry(observationRegistry)
            .build();
        CloseableHttpClient client = client(micrometerHttpRequestExecutor);
        switch (method) {
            case "get":
                execute(client, new HttpGet(server.baseUrl()));
                break;

            case "post":
                execute(client, new HttpPost(server.baseUrl()));
                break;

            default:
                execute(client, new HttpUriRequestBase(method, URI.create(server.baseUrl())));
                break;
        }
        TestObservationRegistryAssert.assertThat(observationRegistry)
            .hasSingleObservationThat()
            .hasContextualNameEqualToIgnoringCase("http " + method);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void protocolExceptionIsTaggedWithIoError(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(any(urlEqualTo("/error")).willReturn(aResponse().withStatus(1)));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                assertThatCode(() -> execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/error").build()))
                    .hasRootCauseInstanceOf(ProtocolException.class);
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                assertThatCode(() -> execute(client, new HttpGet(server.baseUrl() + "/error")))
                    .isInstanceOf(ClientProtocolException.class);
            }
        }
        assertThat(registry.get(DEFAULT_METER_NAME)
            .tags("method", "GET", "status", "IO_ERROR", "outcome", "UNKNOWN")
            .tag("exception", "ProtocolException")
            .timer()
            .count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void socketTimeoutIsTaggedWithIoError(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(get(urlEqualTo("/delayed")).willReturn(aResponse().withStatus(200).withFixedDelay(2100)));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            Future<SimpleHttpResponse> future;
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                client.start();
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.url("/delayed")).build();
                future = client.execute(request, null);
                assertThatCode(future::get).hasRootCauseInstanceOf(SocketTimeoutException.class);
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                assertThatCode(() -> execute(client, new HttpGet(server.baseUrl() + "/delayed")))
                    .isInstanceOf(SocketTimeoutException.class);
            }
        }

        Timer timer = registry.get(DEFAULT_METER_NAME)
            .tag("method", "GET")
            .tag("status", "IO_ERROR")
            .tag("outcome", "UNKNOWN")
            .tag("exception", "SocketTimeoutException")
            .timer();
        logStats("httpStatusCodeIsTaggedWithIoError", timer);
        assertThat(timer.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void connectionRefusedIsTaggedWithIoError(boolean configureObservationRegistry, boolean async) throws IOException {
        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                assertThatCode(() -> execute(client, SimpleRequestBuilder.get("http://localhost:3456").build()))
                    .hasRootCauseInstanceOf(HttpHostConnectException.class);
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                assertThatThrownBy(() -> execute(client, new HttpGet("http://localhost:3456")))
                    .isInstanceOf(HttpHostConnectException.class);
            }
        }
        assertThat(registry.get(DEFAULT_METER_NAME)
            .tags("method", "GET", "status", "IO_ERROR", "outcome", "UNKNOWN")
            .tag("exception", "HttpHostConnectException")
            .timer()
            .count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void connectTimeoutIsTaggedWithIoError(boolean configureObservationRegistry, boolean async)
            throws IOException, ExecutionException, InterruptedException {
        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                assertThatCode(() -> execute(client, SimpleRequestBuilder.get("https://1.1.1.1:2312/").build()))
                    .hasRootCauseInstanceOf(ConnectTimeoutException.class);
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                assertThatThrownBy(() -> execute(client, new HttpGet("https://1.1.1.1:2312/")))
                    .isInstanceOf(ConnectTimeoutException.class);
            }
        }
        assertThat(registry.get(DEFAULT_METER_NAME)
            .tags("method", "GET", "status", "IO_ERROR", "outcome", "UNKNOWN")
            .tag("exception", "ConnectTimeoutException")
            .timer()
            .count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void retriesAreMetered_overall(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {
        server.stubFor(get(urlEqualTo("/error")).willReturn(aResponse().withStatus(503)));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry).meterRetries(false)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        Instant start = Instant.now();
        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/error").build());
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl() + "/error"));
            }
        }

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);
        logger.info("retriesAreMetered_overall: async = {} ,total duration {}", async, duration.toMillis());

        Timer timer = registry.get(DEFAULT_METER_NAME)
            .tags("method", "GET", "status", "503", "outcome", "SERVER_ERROR")
            .timer();

        logStats("retriesAreMetered_overall", timer);
        assertThat(timer.count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void retriesAreMetered_individually(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws IOException, ExecutionException, InterruptedException {

        server.stubFor(get(urlEqualTo("/error")).willReturn(aResponse().withStatus(503)));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .uriMapper(HttpRequest::getRequestUri)
            .meterRetries(true)
            .build();

        Instant start = Instant.now();
        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder, 1)) {
                execute(client, SimpleRequestBuilder.get(server.baseUrl() + "/error").build());
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl() + "/error"));
            }
        }
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        logger.info("retriesAreMetered_individually: async={} total duration {}", async, duration.toMillis());

        Timer timer = registry.get(DEFAULT_METER_NAME)
            .tags("method", "GET", "status", "503", "outcome", "SERVER_ERROR")
            .timer();

        logStats("retriesAreMetered_individually", timer);
        assertThat(timer.count()).isEqualTo(2L);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void testPositiveOutcomeAfterRetry_overall(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws ExecutionException, InterruptedException, IOException {
        server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("Cause Success"));
        server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
            .whenScenarioStateIs("Cause Success")
            .willReturn(aResponse().withStatus(200)));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                client.start();
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.url("/retry")).build();

                Instant start = Instant.now();
                Future<SimpleHttpResponse> future = client.execute(request, null);

                HttpResponse response = future.get();
                Instant end = Instant.now();
                Duration duration = Duration.between(start, end);

                logger.debug("testPositiveOutcomeAfterRetry_overall: total duration {}", duration.toMillis());

                assertThat(response.getCode()).isEqualTo(200);
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl() + "/retry"));
            }
        }

        server.verify(exactly(2), getRequestedFor(urlEqualTo("/retry")));

        Timer timer = registry.get("httpcomponents.httpclient.request")
            .tag("method", "GET")
            .tag("status", "200")
            .tag("outcome", "SUCCESS")
            .timer();

        logStats("testPositiveOutcomeAfterRetry_overall", timer);

        assertThat(timer.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("commonArgs")
    void testPositiveOutcomeAfterRetry_individual(boolean configureObservationRegistry, boolean async,
            @WiremockResolver.Wiremock WireMockServer server)
            throws ExecutionException, InterruptedException, IOException {
        server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("Cause Success"));
        server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
            .whenScenarioStateIs("Cause Success")
            .willReturn(aResponse().withStatus(200)));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry).meterRetries(true)
            .observationRegistry(configureObservationRegistry ? createObservationRegistry() : ObservationRegistry.NOOP)
            .build();

        if (async) {

            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                Instant start;
                Future<SimpleHttpResponse> future;
                client.start();
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.url("/retry")).build();

                start = Instant.now();
                future = client.execute(request, null);
                HttpResponse response = future.get();
                Instant end = Instant.now();
                Duration duration = Duration.between(start, end);
                logger.debug("retriesAreMetered_overall: total duration {}", duration.toMillis());
                assertThat(response.getCode()).isEqualTo(200);
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl() + "/retry"));
            }
        }

        server.verify(exactly(2), getRequestedFor(urlEqualTo("/retry")));

        Timer timer1 = registry.get("httpcomponents.httpclient.request")
            .tag("method", "GET")
            .tag("status", "503")
            .tag("outcome", "SERVER_ERROR")
            .timer();
        assertThat(timer1.count()).isEqualTo(1);

        logStats("testPositiveOutcomeAfterRetry_individual timer1", timer1);

        Timer timer = registry.get("httpcomponents.httpclient.request")
            .tag("method", "GET")
            .tag("status", "200")
            .tag("outcome", "SUCCESS")
            .timer();

        logStats("testPositiveOutcomeAfterRetry_individual timer2", timer);
        assertThat(timer.count()).isEqualTo(1);
    }

    void logStats(String testCase, Timer timer) {
        long count = timer.count();
        double total = timer.totalTime(TimeUnit.MILLISECONDS);
        double max = timer.max(TimeUnit.MILLISECONDS);
        logger.info("{}: count {} total {} max {}", testCase, count, total, max);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void customMeterNameIsUsed(boolean async, @WiremockResolver.Wiremock WireMockServer server)
            throws ExecutionException, InterruptedException, IOException {

        server.stubFor(any(anyUrl()));

        ApacheHttpClientMetricsBinder metricsBinder = builder(registry).meterName("custom.apache.http.client.requests")
            .build();

        if (async) {
            try (CloseableHttpAsyncClient client = asyncClient(metricsBinder)) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl()).build();
                execute(client, request);
            }
        }
        else {
            try (CloseableHttpClient client = client(metricsBinder)) {
                execute(client, new HttpGet(server.baseUrl()));
            }
        }

        Timer timer = registry.get("custom.apache.http.client.requests").timer();
        logStats("testCustomMeterName", timer);
        assertThat(timer.count()).isEqualTo(1L);
    }

    static class CustomGlobalApacheHttpConvention extends DefaultApacheHttpClientObservationConvention
            implements GlobalObservationConvention<ApacheHttpClientContext> {

        @Override
        public String getName() {
            return "custom.apache.http.client.requests";
        }

    }

    // classic
    private CloseableHttpClient client(ApacheHttpClientMetricsBinder metricsBinder) {
        return client(metricsBinder, 1);
    }

    private CloseableHttpClient client(ApacheHttpClientMetricsBinder metricsBinder, int maxRetries) {
        DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(maxRetries,
                TimeValue.ofMilliseconds(500L));

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setSocketTimeout(2000, TimeUnit.MILLISECONDS)
            .setConnectTimeout(2000L, TimeUnit.MILLISECONDS)
            .build();

        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
            .setRetryStrategy(retryStrategy)
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .build());

        return metricsBinder.instrumentAndGet(clientBuilder);
    }

    private ObservationRegistry createObservationRegistry() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new DefaultMeterObservationHandler(registry));
        return observationRegistry;
    }

    // async

    private CloseableHttpAsyncClient asyncClient(ApacheHttpClientMetricsBinder metricsBinder) {
        return asyncClient(metricsBinder, 1);
    }

    private CloseableHttpAsyncClient asyncClient(ApacheHttpClientMetricsBinder metricsBinder, int maxRetries) {

        DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(maxRetries,
                TimeValue.ofMilliseconds(500L));

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setSocketTimeout(2000, TimeUnit.MILLISECONDS)
            .setConnectTimeout(2000L, TimeUnit.MILLISECONDS)
            .build();

        HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom()
            .setRetryStrategy(retryStrategy)
            .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .build());

        return metricsBinder.instrumentAndGet(clientBuilder);
    }

}
