/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.java11.instrument.binder.jdk;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletionException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.BDDAssertions.then;

@WireMockTest
class MicrometerHttpClientTests {

    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    // tag::setupClient[]
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    // end::setupClient[]

    @BeforeEach
    void setup() {
        stubFor(any(urlEqualTo("/metrics")).willReturn(ok().withBody("body")));
        stubFor(any(urlEqualTo("/test-fault"))
            .willReturn(new ResponseDefinitionBuilder().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        stubFor(any(urlEqualTo("/resources/1")).willReturn(notFound()));
    }

    @Test
    void shouldInstrumentHttpClientWithObservation(WireMockRuntimeInfo wmInfo)
            throws IOException, InterruptedException {
        ObservationRegistry observationRegistry = TestObservationRegistry.create();
        observationRegistry.observationConfig()
            .observationHandler(new ObservationHandler.AllMatchingCompositeObservationHandler(headerSettingHandler(),
                    new DefaultMeterObservationHandler(meterRegistry)));

        HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(wmInfo.getHttpBaseUrl() + "/metrics"))
            .build();

        // tag::observationInstrumentation[]
        HttpClient observedClient = MicrometerHttpClient.instrumentationBuilder(httpClient, meterRegistry)
            .observationRegistry(observationRegistry)
            .build();
        // end::observationInstrumentation[]
        observedClient.send(request, HttpResponse.BodyHandlers.ofString());

        verify(anyRequestedFor(urlEqualTo("/metrics")).withHeader("foo", equalTo("bar")));
        thenMeterRegistryContainsHttpClientTags();
    }

    @Test
    void shouldInstrumentHttpClientWithTimer(WireMockRuntimeInfo wmInfo) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(wmInfo.getHttpBaseUrl() + "/metrics"))
            .build();

        // tag::meterRegistryInstrumentation[]
        HttpClient observedClient = MicrometerHttpClient.instrumentationBuilder(httpClient, meterRegistry).build();
        // end::meterRegistryInstrumentation[]
        observedClient.send(request, HttpResponse.BodyHandlers.ofString());

        thenMeterRegistryContainsHttpClientTags();
    }

    @Test
    void shouldInstrumentHttpClientWhenNotFound(WireMockRuntimeInfo wmInfo) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(wmInfo.getHttpBaseUrl() + "/resources/1"))
            .build();

        HttpClient observedClient = MicrometerHttpClient.instrumentationBuilder(httpClient, meterRegistry).build();
        observedClient.send(request, HttpResponse.BodyHandlers.ofString());

        then(meterRegistry.get("http.client.requests")
            .tag("method", "GET")
            .tag("status", "404")
            .tag("outcome", "CLIENT_ERROR")
            .tag("uri", "UNKNOWN")
            .timer()).isNotNull();
    }

    @Test
    void shouldInstrumentHttpClientWithUriPatternHeaderWhenNotFound(WireMockRuntimeInfo wmInfo)
            throws IOException, InterruptedException {
        String uriPattern = "/resources/{id}";

        HttpRequest request = HttpRequest.newBuilder()
            .header(MicrometerHttpClient.URI_PATTERN_HEADER, uriPattern)
            .GET()
            .uri(URI.create(wmInfo.getHttpBaseUrl() + "/resources/1"))
            .build();

        HttpClient observedClient = MicrometerHttpClient.instrumentationBuilder(httpClient, meterRegistry).build();
        observedClient.send(request, HttpResponse.BodyHandlers.ofString());

        then(meterRegistry.get("http.client.requests")
            .tag("method", "GET")
            .tag("status", "404")
            .tag("outcome", "CLIENT_ERROR")
            .tag("uri", uriPattern)
            .timer()).isNotNull();
    }

    @Test
    @Issue("#5136")
    void shouldThrowErrorFromSendAsync(WireMockRuntimeInfo wmInfo) {
        var client = MicrometerHttpClient.instrumentationBuilder(httpClient, meterRegistry).build();

        String uri = "/test-fault";
        var request = HttpRequest.newBuilder(URI.create(wmInfo.getHttpBaseUrl() + uri))
            .header(MicrometerHttpClient.URI_PATTERN_HEADER, uri)
            .GET()
            .build();

        var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        assertThatThrownBy(response::join).isInstanceOf(CompletionException.class);
        assertThatNoException().isThrownBy(() -> meterRegistry.get("http.client.requests")
            .tag("method", "GET")
            .tag("uri", uri)
            .tag("status", "UNKNOWN")
            .tag("outcome", "UNKNOWN")
            .timer());
    }

    @Test
    void sendAsyncShouldSetErrorInContext(WireMockRuntimeInfo wmInfo) {
        ObservationRegistry observationRegistry = TestObservationRegistry.create();
        StoreContextObservationHandler storeContextObservationHandler = new StoreContextObservationHandler();
        observationRegistry.observationConfig().observationHandler(storeContextObservationHandler);

        var request = HttpRequest.newBuilder(URI.create(wmInfo.getHttpBaseUrl() + "/test-fault")).GET().build();

        HttpClient observedClient = MicrometerHttpClient.instrumentationBuilder(httpClient, meterRegistry)
            .observationRegistry(observationRegistry)
            .build();
        var response = observedClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        assertThatThrownBy(response::join).isInstanceOf(CompletionException.class);
        assertThat(storeContextObservationHandler.context.getError()).isInstanceOf(CompletionException.class);
    }

    private void thenMeterRegistryContainsHttpClientTags() {
        then(meterRegistry.find("http.client.requests")
            .tag("method", "GET")
            .tag("status", "200")
            .tag("outcome", "SUCCESS")
            .tag("uri", "UNKNOWN")
            .timer()).isNotNull();
    }

    private ObservationHandler<HttpClientContext> headerSettingHandler() {
        return new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return context instanceof HttpClientContext;
            }

            @Override
            public void onStart(HttpClientContext context) {
                HttpRequest.Builder carrier = context.getCarrier();
                context.getSetter().set(carrier, "foo", "bar");
            }
        };
    }

    static class StoreContextObservationHandler implements ObservationHandler<HttpClientContext> {

        HttpClientContext context;

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof HttpClientContext;
        }

        @Override
        public void onStart(HttpClientContext context) {
            this.context = context;
        }

    }

}
