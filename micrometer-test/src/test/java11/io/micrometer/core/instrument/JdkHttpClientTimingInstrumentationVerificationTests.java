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
package io.micrometer.core.instrument;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.binder.httpcomponents.DefaultUriMapper;
import io.micrometer.core.instrument.binder.jdk.MicrometerHttpClient;
import org.junit.jupiter.api.Tag;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Tag("jdk11")
class JdkHttpClientTimingInstrumentationVerificationTests extends HttpClientTimingInstrumentationVerificationTests {

    private final HttpClient httpClient = MicrometerHttpClient.instrumentationBuilder(
            HttpClient.newBuilder().connectTimeout(Duration.of(100, ChronoUnit.MILLIS)).build(), getRegistry()).build();

    @Override
    protected String timerName() {
        return "http.client.requests";
    }

    @Override
    protected void sendHttpRequest(HttpMethod method, @Nullable byte[] body, URI baseUri, String templatedPath,
            String... pathVariables) {
        try {
            httpClient.send(makeRequest(method, body, baseUri, templatedPath, pathVariables),
                    HttpResponse.BodyHandlers.ofString());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpRequest makeRequest(HttpMethod method, @Nullable byte[] body, URI baseUri, String templatedPath,
            String... pathVariables) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().method(method.name(),
                body != null ? HttpRequest.BodyPublishers.ofByteArray(body) : HttpRequest.BodyPublishers.noBody());
        builder.uri(URI.create(baseUri + substitutePathVariables(templatedPath, pathVariables)));
        builder.setHeader(DefaultUriMapper.URI_PATTERN_HEADER, templatedPath);
        return builder.build();
    }

}
