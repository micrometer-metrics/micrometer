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

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.HttpClientTimingInstrumentationVerificationTests;
import io.micrometer.observation.docs.ObservationDocumentation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

class JdkHttpClientTimingInstrumentationVerificationTests
        extends HttpClientTimingInstrumentationVerificationTests<HttpClient> {

    @Override
    protected HttpClient clientInstrumentedWithMetrics() {
        return getInstrumentationBuilder().build();
    }

    @Nullable
    @Override
    protected HttpClient clientInstrumentedWithObservations() {
        return getInstrumentationBuilder().observationRegistry(getObservationRegistry()).build();
    }

    private MicrometerHttpClient.InstrumentationBuilder getInstrumentationBuilder() {
        return MicrometerHttpClient.instrumentationBuilder(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build(), getRegistry());
    }

    @Override
    protected void sendHttpRequest(HttpClient instrumentedClient, HttpMethod method, @Nullable byte[] body, URI baseUri,
            String templatedPath, String... pathVariables) {
        try {
            instrumentedClient.send(makeRequest(method, body, baseUri, templatedPath, pathVariables),
                    HttpResponse.BodyHandlers.ofString());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected ObservationDocumentation observationDocumentation() {
        return HttpClientObservationDocumentation.HTTP_CALL;
    }

    private HttpRequest makeRequest(HttpMethod method, @Nullable byte[] body, URI baseUri, String templatedPath,
            String... pathVariables) {
        return HttpRequest.newBuilder()
            .method(method.name(),
                    body != null ? HttpRequest.BodyPublishers.ofByteArray(body) : HttpRequest.BodyPublishers.noBody())
            .uri(URI.create(baseUri + substitutePathVariables(templatedPath, pathVariables)))
            .setHeader(MicrometerHttpClient.URI_PATTERN_HEADER, templatedPath)
            .build();
    }

}
