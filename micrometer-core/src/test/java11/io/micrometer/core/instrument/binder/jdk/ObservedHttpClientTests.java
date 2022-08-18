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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@WireMockTest
public class ObservedHttpClientTests {

    @Test
    void shouldInstrumentHttpClient(WireMockRuntimeInfo wmInfo) throws IOException, InterruptedException {
        TestObservationRegistry observationRegistry = TestObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(headerSettingHandler());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.of(100, ChronoUnit.MILLIS)).build();

        stubFor(any(urlEqualTo("/metrics")).willReturn(ok().withBody("body")));

        HttpClient observedClient = new ObservedHttpClient(observationRegistry, httpClient);

        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(wmInfo.getHttpBaseUrl() + "/metrics"))
                .build();
        observedClient.send(request, HttpResponse.BodyHandlers.ofString());

        verify(anyRequestedFor(urlEqualTo("/metrics")).withHeader("foo", equalTo("bar")));
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
