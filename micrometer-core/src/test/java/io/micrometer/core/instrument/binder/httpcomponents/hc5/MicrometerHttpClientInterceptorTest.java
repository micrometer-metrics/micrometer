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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link MicrometerHttpClientInterceptor}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
@ExtendWith(WiremockResolver.class)
class MicrometerHttpClientInterceptorTest {

    private static final InternalLogger logger = InternalLoggerFactory
        .getInstance(MicrometerHttpClientInterceptorTest.class);

    private MeterRegistry registry;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry();
        // registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    }

    @Test
    void asyncRequest(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(any(anyUrl()));

        CloseableHttpAsyncClient client = asyncClient();
        client.start();
        SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl()).build();

        Future<SimpleHttpResponse> future = client.execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getCode()).isEqualTo(200);
        assertThatCode(() -> {
            Timer timer = registry.get("httpcomponents.httpclient.request")
                .tag("method", "GET")
                .tag("status", "200")
                .tag("outcome", "SUCCESS")
                .timer();
            logStats("asyncRequest", timer);
            assertThat(timer.count()).isEqualTo(1);
        }).doesNotThrowAnyException();

        client.close();
    }

    @Test
    void httpStatusCodeIsTaggedWithIoError(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(get(urlEqualTo("/delayed")).willReturn(aResponse().withStatus(200).withFixedDelay(2000)));

        CloseableHttpAsyncClient client = asyncClient();
        client.start();
        SimpleHttpRequest request = SimpleRequestBuilder.get(server.url("/delayed")).build();

        Future<SimpleHttpResponse> future = client.execute(request, null);

        assertThatCode(future::get).hasRootCauseInstanceOf(SocketTimeoutException.class);

        assertThatCode(() -> {
            Timer timer = registry.get("httpcomponents.httpclient.request")
                .tag("method", "GET")
                .tag("status", "IO_ERROR")
                .tag("outcome", "UNKNOWN")
                // .tag("exception", "SocketTimeoutException")
                .timer();
            logStats("httpStatusCodeIsTaggedWithIoError", timer);
            assertThat(timer.count()).isEqualTo(1);
        }).doesNotThrowAnyException();

        client.close();
    }

    @Test
    void retriesAreMetered_overall(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(get(urlEqualTo("/err")).willReturn(aResponse().withStatus(503)));

        int maxRetries = 4;
        CloseableHttpAsyncClient client = asyncClient(maxRetries, false);
        client.start();
        SimpleHttpRequest request = SimpleRequestBuilder.get(server.url("/err")).build();

        Instant start = Instant.now();
        Future<SimpleHttpResponse> future = client.execute(request, null);

        HttpResponse response = future.get();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        logger.debug("retriesAreMetered_overall: total duration {}", duration.toMillis());

        assertThat(response.getCode()).isEqualTo(503);

        assertThatCode(() -> {
            Timer timer = registry.get("httpcomponents.httpclient.request")
                .tag("method", "GET")
                .tag("status", "503")
                .tag("outcome", "SERVER_ERROR")
                .timer();

            logStats("retriesAreMetered_overall", timer);

            assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isLessThan(duration.toMillis());
            assertThat(timer.count()).isEqualTo(1);
        }).doesNotThrowAnyException();

        client.close();
    }

    @Test
    void retriesAreMetered_individually(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(get(urlEqualTo("/err")).willReturn(aResponse().withStatus(503)));

        int maxRetries = 4;
        CloseableHttpAsyncClient client = asyncClient(maxRetries, true);
        client.start();
        SimpleHttpRequest request = SimpleRequestBuilder.get(server.url("/err")).build();

        Instant start = Instant.now();
        Future<SimpleHttpResponse> future = client.execute(request, null);

        HttpResponse response = future.get();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        logger.debug("retriesAreMetered_overall: total duration {}", duration.toMillis());

        assertThat(response.getCode()).isEqualTo(503);

        assertThatCode(() -> {
            Timer timer = registry.get("httpcomponents.httpclient.request")
                .tag("method", "GET")
                .tag("status", "503")
                .tag("outcome", "SERVER_ERROR")
                .timer();

            logStats("retriesAreMetered_overall", timer);

            assertThat(timer.count()).isEqualTo(maxRetries + 1);
        }).doesNotThrowAnyException();

        client.close();
    }

    @Test
    void testPositveOutcomeAfterRetry_overall(@WiremockResolver.Wiremock WireMockServer server)
            throws ExecutionException, InterruptedException {
        server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("Cause Success"));
        server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
            .whenScenarioStateIs("Cause Success")
            .willReturn(aResponse().withStatus(200)));

        CloseableHttpAsyncClient client = asyncClient(1, false);
        client.start();
        SimpleHttpRequest request = SimpleRequestBuilder.get(server.url("/retry")).build();

        Instant start = Instant.now();
        Future<SimpleHttpResponse> future = client.execute(request, null);

        HttpResponse response = future.get();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        logger.debug("retriesAreMetered_overall: total duration {}", duration.toMillis());

        assertThat(response.getCode()).isEqualTo(200);
        server.verify(exactly(2), getRequestedFor(urlEqualTo("/retry")));

        assertThatCode(() -> {
            Timer timer = registry.get("httpcomponents.httpclient.request")
                .tag("method", "GET")
                .tag("status", "200")
                .tag("outcome", "SUCCESS")
                .timer();

            logStats("retriesAreMetered_overall", timer);

            assertThat(timer.count()).isEqualTo(1);
        }).doesNotThrowAnyException();

    }

    @Test
    void testPositveOutcomeAfterRetry_individual(@WiremockResolver.Wiremock WireMockServer server)
            throws ExecutionException, InterruptedException {
        server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("Cause Success"));
        server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
            .whenScenarioStateIs("Cause Success")
            .willReturn(aResponse().withStatus(200)));

        CloseableHttpAsyncClient client = asyncClient(1, true);
        client.start();
        SimpleHttpRequest request = SimpleRequestBuilder.get(server.url("/retry")).build();

        Instant start = Instant.now();
        Future<SimpleHttpResponse> future = client.execute(request, null);

        HttpResponse response = future.get();
        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);

        logger.debug("retriesAreMetered_overall: total duration {}", duration.toMillis());

        assertThat(response.getCode()).isEqualTo(200);

        server.verify(exactly(2), getRequestedFor(urlEqualTo("/retry")));

        assertThatCode(() -> {
            Timer timer1 = registry.get("httpcomponents.httpclient.request")
                .tag("method", "GET")
                .tag("status", "503")
                .tag("outcome", "SERVER_ERROR")
                .timer();
            assertThat(timer1.count()).isEqualTo(1);
            logStats("testPositveOutcomeAfterRetry_individual timer1", timer1);

            Timer timer = registry.get("httpcomponents.httpclient.request")
                .tag("method", "GET")
                .tag("status", "200")
                .tag("outcome", "SUCCESS")
                .timer();

            logStats("testPositveOutcomeAfterRetry_individual timer2", timer);
            assertThat(timer.count()).isEqualTo(1);
        }).doesNotThrowAnyException();

    }

    void logStats(String testCase, Timer timer) {
        if (logger.isDebugEnabled()) {
            long count = timer.count();
            double total = timer.totalTime(TimeUnit.MILLISECONDS);
            double max = timer.max(TimeUnit.MILLISECONDS);
            logger.debug("{}: count {} total {} max {}", testCase, count, total, max);
        }
    }

    @Test
    void connectionRefusedIsTaggedWithIoError(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(get(urlEqualTo("/delayed")).willReturn(aResponse().withStatus(200).withFixedDelay(2500)));

        CloseableHttpAsyncClient client = asyncClient();
        client.start();
        SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost:3456").build();

        Future<SimpleHttpResponse> future = client.execute(request, null);

        assertThatCode(future::get).hasRootCauseInstanceOf(HttpHostConnectException.class);

        assertThatCode(() -> {
            Timer timer = registry.get("httpcomponents.httpclient.request")
                .tag("method", "GET")
                .tag("status", "IO_ERROR")
                .tag("outcome", "UNKNOWN")
                // .tag("exception", "SocketTimeoutException")
                .timer();
            logStats("connectionRefusedIsTaggedWithIoError", timer);
            assertThat(timer.count()).isEqualTo(1);
        }).doesNotThrowAnyException();

        client.close();
    }

    @Test
    void connectTimeoutIsTaggedWithIoError(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(get(urlEqualTo("/delayed")).willReturn(aResponse().withStatus(200).withFixedDelay(2500)));

        CloseableHttpAsyncClient client = asyncClient();
        client.start();
        // needs a firewalled host.
        SimpleHttpRequest request = SimpleRequestBuilder.get("https://1.1.1.1:2312/").build();

        Future<SimpleHttpResponse> future = client.execute(request, null);
        assertThatCode(future::get).hasRootCauseInstanceOf(ConnectTimeoutException.class);

        assertThatCode(() -> {
            Timer timer = registry.get("httpcomponents.httpclient.request")
                .tag("method", "GET")
                .tag("status", "IO_ERROR")
                .tag("outcome", "UNKNOWN")
                .timer();
            logStats("connectTimeoutIsTaggedWithIoError", timer);
            assertThat(timer.count()).isEqualTo(1);
        }).doesNotThrowAnyException();

        client.close();
    }

    @Test
    void uriIsReadFromHttpHeader(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(any(anyUrl()));
        MicrometerHttpClientInterceptor interceptor = new MicrometerHttpClientInterceptor(registry, Tags.empty(), true,
                false);
        CloseableHttpAsyncClient client = asyncClient(interceptor, 1, false);
        client.start();
        SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl()).build();
        request.addHeader(DefaultUriMapper.URI_PATTERN_HEADER, "/some/pattern");

        Future<SimpleHttpResponse> future = client.execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(registry.get("httpcomponents.httpclient.request")
            .tag("uri", "/some/pattern")
            .tag("status", "200")
            .timer()
            .count()).isEqualTo(1);

        client.close();
    }

    private CloseableHttpAsyncClient asyncClient() {
        return asyncClient(1, false);
    }

    private CloseableHttpAsyncClient asyncClient(int maxRetries, boolean meterRetries) {
        MicrometerHttpClientInterceptor interceptor = new MicrometerHttpClientInterceptor(registry,
                HttpRequest::getRequestUri, Tags.empty(), true, meterRetries);
        return asyncClient(interceptor, maxRetries, meterRetries);
    }

    private CloseableHttpAsyncClient asyncClient(MicrometerHttpClientInterceptor interceptor, int maxRetries,
            boolean meterRetries) {
        DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(maxRetries,
                TimeValue.ofMilliseconds(500L));

        HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom()
            .setRetryStrategy(retryStrategy)
            .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setSocketTimeout(2000, TimeUnit.MILLISECONDS)
                    .setConnectTimeout(2000L, TimeUnit.MILLISECONDS)
                    .build())
                .build());

        if (meterRetries) {
            clientBuilder.addExecInterceptorAfter(ChainElement.RETRY.name(), "custom",
                    interceptor.getExecChainHandler());
        }
        else {
            clientBuilder.addExecInterceptorFirst("custom", interceptor.getExecChainHandler());
        }
        return clientBuilder.build();
    }

}
