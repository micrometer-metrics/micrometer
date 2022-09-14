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
import io.micrometer.core.instrument.binder.jetty.JettyClientMetrics;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.junit.jupiter.api.BeforeEach;

import java.net.URI;

class JettyClientTimingInstrumentationVerificationTests
        extends HttpClientTimingInstrumentationVerificationTests<HttpClient> {

    private static final String HEADER_URI_PATTERN = "URI_PATTERN";

    private final HttpClient httpClient = new HttpClient();

    @Override
    protected String timerName() {
        return "jetty.client.requests";
    }

    @BeforeEach
    void setup() throws Exception {
        httpClient.start();
    }

    @Override
    protected HttpClient clientInstrumentedWithMetrics() {
        httpClient.getRequestListeners().add(JettyClientMetrics
                .builder(getRegistry(), result -> result.getRequest().getHeaders().get(HEADER_URI_PATTERN)).build());
        return httpClient;
    }

    @Nullable
    @Override
    protected HttpClient clientInstrumentedWithObservations() {
        return null;
    }

    @Override
    protected void sendHttpRequest(HttpClient instrumentedClient, HttpMethod method, @Nullable byte[] body, URI baseUri,
            String templatedPath, String... pathVariables) {
        try {
            Request request = instrumentedClient
                    .newRequest(baseUri + substitutePathVariables(templatedPath, pathVariables)).method(method.name())
                    .header(HEADER_URI_PATTERN, templatedPath);
            if (body != null) {
                request.content(new BytesContentProvider(body));
            }
            request.send();
            instrumentedClient.stop();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
