/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jdk;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("deprecation")
@WireMockTest
class MicrometerHttpClientTests {

    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @BeforeEach
    void setup() {
        stubFor(any(urlEqualTo("/metrics")).willReturn(ok().withBody("body")));
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

        HttpClient observedClient = MicrometerHttpClient.instrumentationBuilder(httpClient, meterRegistry)
            .observationRegistry(observationRegistry)
            .build();
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

        HttpClient observedClient = MicrometerHttpClient.instrumentationBuilder(httpClient, meterRegistry).build();
        observedClient.send(request, HttpResponse.BodyHandlers.ofString());

        thenMeterRegistryContainsHttpClientTags();
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

}
