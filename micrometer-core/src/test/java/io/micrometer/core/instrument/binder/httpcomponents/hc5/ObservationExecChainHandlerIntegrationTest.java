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
import io.micrometer.observation.tck.TestObservationRegistry;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientObservationDocumentation.ApacheHttpClientKeyNames.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Wiremock-based integration tests for {@link ObservationExecChainHandler}.
 *
 * @author Lars Uffmann
 * @author Brian Clozel
 */
@ExtendWith(WiremockResolver.class)
class ObservationExecChainHandlerIntegrationTest {

    private static final String DEFAULT_METER_NAME = "httpcomponents.httpclient.request";

    private static final HttpClientResponseHandler<ClassicHttpResponse> NOOP_RESPONSE_HANDLER = (response) -> response;

    private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    @Nested
    class ClassicClient {

        @Test
        void recordSuccessfulExchanges(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(anyUrl()));
            // tag::example_classic[]
            try (CloseableHttpClient client = classicClient()) {
                executeClassic(client, new HttpGet(server.baseUrl()));
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("SUCCESS"))
                .hasLowCardinalityKeyValue(STATUS.withValue("200"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"));
            // end::example_classic[]
        }

        @Test
        void recordClientErrorExchanges(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(urlEqualTo("/notfound")).willReturn(aResponse().withStatus(404)));
            try (CloseableHttpClient client = classicClient()) {
                executeClassic(client, new HttpGet(server.baseUrl() + "/notfound"));
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("CLIENT_ERROR"))
                .hasLowCardinalityKeyValue(STATUS.withValue("404"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"))
                .hasLowCardinalityKeyValue(URI.withValue("UNKNOWN"));
        }

        @Test
        void recordClientErrorExchangesWithUriPatternHeader(@WiremockResolver.Wiremock WireMockServer server)
                throws Exception {
            String uriPattern = "/resources/{id}";

            server.stubFor(any(urlEqualTo("/resources/1")).willReturn(aResponse().withStatus(404)));
            try (CloseableHttpClient client = classicClient()) {
                HttpGet request = new HttpGet(server.baseUrl() + "/resources/1");
                request.addHeader(DefaultUriMapper.URI_PATTERN_HEADER, uriPattern);
                executeClassic(client, request);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("CLIENT_ERROR"))
                .hasLowCardinalityKeyValue(STATUS.withValue("404"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"))
                .hasLowCardinalityKeyValue(URI.withValue(uriPattern));
        }

        @Test
        void recordUnknownUriPatternByDefault(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(anyUrl()));
            try (CloseableHttpClient client = classicClient()) {
                executeClassic(client, new HttpGet(server.baseUrl() + "/someuri"));
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(URI.withValue("UNKNOWN"));
        }

        @Test
        @SuppressWarnings("deprecation")
        void recordUriPatternWhenHeader(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(anyUrl()));
            try (CloseableHttpClient client = classicClient()) {
                HttpGet getWithHeader = new HttpGet(server.baseUrl() + "/some/value");
                getWithHeader.addHeader(DefaultUriMapper.URI_PATTERN_HEADER, "/some/{name}");
                executeClassic(client, getWithHeader);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(URI.withValue("/some/{name}"));
        }

        @Test
        void recordUriPatternWhenContext(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(anyUrl()));
            try (CloseableHttpClient client = classicClient()) {
                HttpClientContext clientContext = HttpClientContext.create();
                clientContext.setAttribute(ApacheHttpClientObservationConvention.URI_TEMPLATE_ATTRIBUTE,
                        "/some/{name}");
                executeClassic(client, clientContext, new HttpGet(server.baseUrl() + "/some/value"));
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(URI.withValue("/some/{name}"));
        }

        @ParameterizedTest
        @ValueSource(strings = { "GET", "PUT", "POST", "DELETE" })
        void contextualNameContainsMethod(String methodName, @WiremockResolver.Wiremock WireMockServer server)
                throws Exception {
            server.stubFor(any(anyUrl()));
            try (CloseableHttpClient client = classicClient()) {
                HttpUriRequestBase request = new HttpUriRequestBase(methodName, java.net.URI.create(server.baseUrl()));
                executeClassic(client, request);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasContextualNameEqualTo("HTTP " + methodName)
                .hasLowCardinalityKeyValue(METHOD.withValue(methodName));
        }

        @Test
        void recordProtocolErrorsAsIoErrors(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(urlEqualTo("/error")).willReturn(aResponse().withStatus(1)));
            try (CloseableHttpClient client = classicClient()) {
                assertThatThrownBy(() -> executeClassic(client, new HttpGet(server.baseUrl() + "/error")))
                    .hasRootCauseInstanceOf(ProtocolException.class);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("UNKNOWN"))
                .hasLowCardinalityKeyValue(STATUS.withValue("IO_ERROR"))
                .hasLowCardinalityKeyValue(EXCEPTION.withValue("ProtocolException"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"));
        }

        @Test
        void recordSocketTimeoutAsIoErrors(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(get(urlEqualTo("/delayed")).willReturn(aResponse().withStatus(200).withFixedDelay(600)));
            try (CloseableHttpClient client = classicClient()) {
                assertThatThrownBy(() -> executeClassic(client, new HttpGet(server.baseUrl() + "/delayed")))
                    .isInstanceOf(SocketTimeoutException.class);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("UNKNOWN"))
                .hasLowCardinalityKeyValue(STATUS.withValue("IO_ERROR"))
                .hasLowCardinalityKeyValue(EXCEPTION.withValue("SocketTimeoutException"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"));
        }

        @Test
        void recordRequestRetries(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(get(urlEqualTo("/error")).willReturn(aResponse().withStatus(503)));
            try (CloseableHttpClient client = classicClient()) {
                executeClassic(client, new HttpGet(server.baseUrl() + "/error"));
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("SERVER_ERROR"))
                .hasLowCardinalityKeyValue(STATUS.withValue("503"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"));
            assertThat(observationRegistry).hasNumberOfObservationsWithNameEqualTo(DEFAULT_METER_NAME, 2);
        }

        @Test
        void recordRequestRetriesWithSuccess(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Success"));
            server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
                .whenScenarioStateIs("Success")
                .willReturn(aResponse().withStatus(200)));

            try (CloseableHttpClient client = classicClient()) {
                executeClassic(client, new HttpGet(server.baseUrl() + "/retry"));
            }
            assertThat(observationRegistry).hasAnObservationWithAKeyValue(OUTCOME.withValue("SUCCESS"))
                .hasAnObservationWithAKeyValue(OUTCOME.withValue("SERVER_ERROR"))
                .hasNumberOfObservationsWithNameEqualTo(DEFAULT_METER_NAME, 2);
        }

        @Test
        void recordAggregateRetriesWithSuccess(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Success"));
            server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
                .whenScenarioStateIs("Success")
                .willReturn(aResponse().withStatus(200)));

            try (CloseableHttpClient client = classicClient_aggregateRetries()) {
                executeClassic(client, new HttpGet(server.baseUrl() + "/retry"));
            }
            assertThat(observationRegistry).hasAnObservationWithAKeyValue(OUTCOME.withValue("SUCCESS"))
                .doesNotHaveAnyRemainingCurrentObservation();
        }

        @Test
        void targetHostPortAndSchemeShouldBeProvidedEvenWhenHttpHostConnectExceptionIsThrown() throws IOException {
            try (CloseableHttpClient client = classicClient()) {
                assertThatExceptionOfType(HttpHostConnectException.class)
                    .isThrownBy(() -> executeClassic(client, new HttpGet("http://localhost:777/123")));
            }
            assertThat(observationRegistry).hasAnObservationWithAKeyValue(TARGET_HOST.withValue("localhost"))
                .hasAnObservationWithAKeyValue(TARGET_PORT.withValue("777"))
                .hasAnObservationWithAKeyValue(TARGET_SCHEME.withValue("http"))
                .hasNumberOfObservationsWithNameEqualTo(DEFAULT_METER_NAME, 1);
        }

    }

    @Nested
    class AsyncClient {

        @Test
        void recordSuccessfulExchanges(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(anyUrl()));
            // tag::example_async[]
            try (CloseableHttpAsyncClient client = asyncClient()) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl()).build();
                executeAsync(client, request);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("SUCCESS"))
                .hasLowCardinalityKeyValue(STATUS.withValue("200"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"));
            // end::example_async[]
        }

        @Test
        void recordClientErrorExchanges(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(urlEqualTo("/notfound")).willReturn(aResponse().withStatus(404)));
            try (CloseableHttpAsyncClient client = asyncClient()) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl() + "/notfound").build();
                executeAsync(client, request);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("CLIENT_ERROR"))
                .hasLowCardinalityKeyValue(STATUS.withValue("404"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"))
                .hasLowCardinalityKeyValue(URI.withValue("UNKNOWN"));
        }

        @Test
        void recordClientErrorExchangesWithUriPatternHeader(@WiremockResolver.Wiremock WireMockServer server)
                throws Exception {
            String uriPattern = "/resources/{id}";

            server.stubFor(any(urlEqualTo("/resources/1")).willReturn(aResponse().withStatus(404)));
            try (CloseableHttpAsyncClient client = asyncClient()) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl() + "/resources/1").build();
                request.addHeader(DefaultUriMapper.URI_PATTERN_HEADER, uriPattern);
                executeAsync(client, request);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("CLIENT_ERROR"))
                .hasLowCardinalityKeyValue(STATUS.withValue("404"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"))
                .hasLowCardinalityKeyValue(URI.withValue(uriPattern));
        }

        @Test
        void recordUnknownUriPatternByDefault(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(anyUrl()));
            try (CloseableHttpAsyncClient client = asyncClient()) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl() + "/someuri").build();
                executeAsync(client, request);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(URI.withValue("UNKNOWN"));
        }

        @Test
        @SuppressWarnings("deprecation")
        void recordUriPatternWhenHeader(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(anyUrl()));
            try (CloseableHttpAsyncClient client = asyncClient()) {
                executeAsync(client,
                        SimpleRequestBuilder.get(server.baseUrl() + "/some/value")
                            .addHeader(DefaultUriMapper.URI_PATTERN_HEADER, "/some/{name}")
                            .build());
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(URI.withValue("/some/{name}"));
        }

        @Test
        void recordUriPatternWhenContext(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(anyUrl()));
            try (CloseableHttpAsyncClient client = asyncClient()) {
                HttpClientContext clientContext = HttpClientContext.create();
                clientContext.setAttribute(ApacheHttpClientObservationConvention.URI_TEMPLATE_ATTRIBUTE,
                        "/some/{name}");
                executeAsync(client, clientContext, SimpleRequestBuilder.get(server.baseUrl() + "/some/value").build());
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(URI.withValue("/some/{name}"));
        }

        @ParameterizedTest
        @ValueSource(strings = { "GET", "PUT", "POST", "DELETE" })
        void contextualNameContainsMethod(String methodName, @WiremockResolver.Wiremock WireMockServer server)
                throws Exception {
            server.stubFor(any(anyUrl()));
            try (CloseableHttpAsyncClient client = asyncClient()) {
                SimpleRequestBuilder request = SimpleRequestBuilder.create(methodName);
                request.setUri(server.baseUrl());
                executeAsync(client, request.build());
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasContextualNameEqualTo("HTTP " + methodName)
                .hasLowCardinalityKeyValue(METHOD.withValue(methodName));
        }

        @Test
        void recordProtocolErrorsAsIoErrors(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(any(urlEqualTo("/error")).willReturn(aResponse().withStatus(1)));
            try (CloseableHttpAsyncClient client = asyncClient()) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl() + "/error").build();
                assertThatThrownBy(() -> executeAsync(client, request)).hasRootCauseInstanceOf(ProtocolException.class);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("UNKNOWN"))
                .hasLowCardinalityKeyValue(STATUS.withValue("IO_ERROR"))
                .hasLowCardinalityKeyValue(EXCEPTION.withValue("ProtocolException"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"));
        }

        @Test
        void recordSocketTimeoutAsIoErrors(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(get(urlEqualTo("/delayed")).willReturn(aResponse().withStatus(200).withFixedDelay(1600)));
            try (CloseableHttpAsyncClient client = asyncClient()) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl() + "/delayed").build();
                assertThatThrownBy(() -> executeAsync(client, request))
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("UNKNOWN"))
                .hasLowCardinalityKeyValue(STATUS.withValue("IO_ERROR"))
                .hasLowCardinalityKeyValue(EXCEPTION.withValue("SocketTimeoutException"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"));
        }

        @Test
        void recordRequestRetries(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(get(urlEqualTo("/error")).willReturn(aResponse().withStatus(503)));
            try (CloseableHttpAsyncClient client = asyncClient()) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl() + "/error").build();
                executeAsync(client, request);
            }
            assertThat(observationRegistry).hasObservationWithNameEqualTo(DEFAULT_METER_NAME)
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("SERVER_ERROR"))
                .hasLowCardinalityKeyValue(STATUS.withValue("503"))
                .hasLowCardinalityKeyValue(METHOD.withValue("GET"));
            assertThat(observationRegistry).hasNumberOfObservationsWithNameEqualTo(DEFAULT_METER_NAME, 2);
        }

        @Test
        void recordRequestRetriesWithSuccess(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Success"));
            server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
                .whenScenarioStateIs("Success")
                .willReturn(aResponse().withStatus(200)));
            try (CloseableHttpAsyncClient client = asyncClient()) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl() + "/retry").build();
                executeAsync(client, request);
            }
            assertThat(observationRegistry).hasAnObservationWithAKeyValue(OUTCOME.withValue("SUCCESS"))
                .hasAnObservationWithAKeyValue(OUTCOME.withValue("SERVER_ERROR"))
                .hasNumberOfObservationsWithNameEqualTo(DEFAULT_METER_NAME, 2);
        }

        @Test
        void recordAggregateRetriesWithSuccess(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
            server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Success"));
            server.stubFor(get(urlEqualTo("/retry")).inScenario("Retry Scenario")
                .whenScenarioStateIs("Success")
                .willReturn(aResponse().withStatus(200)));
            try (CloseableHttpAsyncClient client = asyncClient_aggregateRetries()) {
                SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl() + "/retry").build();
                executeAsync(client, request);
            }
            assertThat(observationRegistry).hasAnObservationWithAKeyValue(OUTCOME.withValue("SUCCESS"))
                .doesNotHaveAnyRemainingCurrentObservation();
        }

    }

    private CloseableHttpClient classicClient() {
        DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(1,
                TimeValue.ofMilliseconds(500L));

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setSocketTimeout(500, TimeUnit.MILLISECONDS)
            .setConnectTimeout(2000L, TimeUnit.MILLISECONDS)
            .build();

        // tag::setup_classic[]
        HttpClientBuilder clientBuilder = HttpClients.custom()
            .setRetryStrategy(retryStrategy)
            .addExecInterceptorAfter(ChainElement.RETRY.name(), "micrometer",
                    new ObservationExecChainHandler(observationRegistry))
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .build());
        // end::setup_classic[]

        return clientBuilder.build();
    }

    private CloseableHttpClient classicClient_aggregateRetries() {
        DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(1,
                TimeValue.ofMilliseconds(500L));

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setSocketTimeout(500, TimeUnit.MILLISECONDS)
            .setConnectTimeout(2000L, TimeUnit.MILLISECONDS)
            .build();

        HttpClientBuilder clientBuilder = HttpClients.custom()
            .setRetryStrategy(retryStrategy)
            .addExecInterceptorFirst("micrometer", new ObservationExecChainHandler(observationRegistry))
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .build());

        return clientBuilder.build();
    }

    private void executeClassic(CloseableHttpClient client, ClassicHttpRequest request) throws IOException {
        EntityUtils.consume(client.execute(request, NOOP_RESPONSE_HANDLER).getEntity());
    }

    private void executeClassic(CloseableHttpClient client, HttpContext context, ClassicHttpRequest request)
            throws IOException {
        EntityUtils.consume(client.execute(request, context, NOOP_RESPONSE_HANDLER).getEntity());
    }

    private CloseableHttpAsyncClient asyncClient() {
        DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(1,
                TimeValue.ofMilliseconds(500L));

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setSocketTimeout(500, TimeUnit.MILLISECONDS)
            .setConnectTimeout(1000, TimeUnit.MILLISECONDS)
            .build();

        // tag::setup_async[]
        HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom()
            .addExecInterceptorAfter(ChainElement.RETRY.name(), "micrometer",
                    new ObservationExecChainHandler(observationRegistry))
            .setRetryStrategy(retryStrategy)
            .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .build());
        // end::setup_async[]

        return clientBuilder.build();
    }

    private CloseableHttpAsyncClient asyncClient_aggregateRetries() {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setSocketTimeout(500, TimeUnit.MILLISECONDS)
            .setConnectTimeout(1000, TimeUnit.MILLISECONDS)
            .build();

        DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(1,
                TimeValue.ofMilliseconds(500L));

        // tag::setup_async_aggregate_retries[]
        HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom()
            .addExecInterceptorFirst("micrometer", new ObservationExecChainHandler(observationRegistry))
            .setRetryStrategy(retryStrategy)
            .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .build());
        // end::setup_async_aggregate_retries[]

        return clientBuilder.build();
    }

    private void executeAsync(CloseableHttpAsyncClient client, SimpleHttpRequest request)
            throws ExecutionException, InterruptedException {
        client.start();
        client.execute(request, null).get();
    }

    private void executeAsync(CloseableHttpAsyncClient client, HttpContext context, SimpleHttpRequest request)
            throws ExecutionException, InterruptedException {
        client.start();
        client.execute(request, context, null).get();
    }

}
