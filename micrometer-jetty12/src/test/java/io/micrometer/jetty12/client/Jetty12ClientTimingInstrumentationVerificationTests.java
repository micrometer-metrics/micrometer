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
package io.micrometer.jetty12.client;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.HttpClientTimingInstrumentationVerificationTests;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;

import java.net.URI;

class Jetty12ClientTimingInstrumentationVerificationTests
        extends HttpClientTimingInstrumentationVerificationTests<HttpClient> {

    private static final String HEADER_URI_PATTERN = "URI_PATTERN";

    @Override
    protected String timerName() {
        return "jetty.client.requests";
    }

    @Override
    protected ObservationDocumentation observationDocumentation() {
        return JettyClientObservationDocumentation.DEFAULT;
    }

    @Override
    protected HttpClient clientInstrumentedWithMetrics() {
        return createHttpClient(false);
    }

    @Nullable
    @Override
    protected HttpClient clientInstrumentedWithObservations() {
        return createHttpClient(true);
    }

    @Override
    protected void sendHttpRequest(HttpClient instrumentedClient, HttpMethod method, @Nullable byte[] body, URI baseUri,
            String templatedPath, String... pathVariables) {
        try {
            Request request = instrumentedClient
                .newRequest(baseUri + substitutePathVariables(templatedPath, pathVariables))
                .method(method.name())
                .headers(httpFields -> httpFields.add(HEADER_URI_PATTERN, templatedPath));
            if (body != null) {
                request.body(new BytesRequestContent(body));
            }
            request.send();
            instrumentedClient.stop();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpClient createHttpClient(boolean withObservationRegistry) {
        HttpClient httpClient = new HttpClient();
        JettyClientMetrics.Builder builder = JettyClientMetrics.builder(getRegistry(),
                (request, result) -> request.getHeaders().get(HEADER_URI_PATTERN));
        if (withObservationRegistry) {
            builder.observationRegistry(getObservationRegistry());
        }
        httpClient.getRequestListeners().addListener(builder.build());
        try {
            httpClient.start();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return httpClient;
    }

}
