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

import io.micrometer.core.instrument.binder.jetty.JettyClientMetrics;
import io.micrometer.core.lang.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.junit.jupiter.api.BeforeEach;

import java.net.URI;

class JettyClientTimingInstrumentationVerificationTests extends HttpClientTimingInstrumentationVerificationTests {

    private final HttpClient httpClient = new HttpClient();

    @Override
    protected String timerName() {
        return "jetty.client.requests";
    }

    @BeforeEach
    void setup() throws Exception {
        httpClient.getRequestListeners().add(JettyClientMetrics
                .builder(getRegistry(), result -> result.getRequest().getHeaders().get("URI_PATTERN")).build());
        httpClient.start();
    }

    @Override
    protected void sendHttpRequest(HttpMethod method, @Nullable byte[] body, URI baseUri, String templatedPath,
            String... pathVariables) {
        try {
            Request request = httpClient.newRequest(baseUri + substitutePathVariables(templatedPath, pathVariables))
                    .method(method.name()).header("URI_PATTERN", templatedPath);
            if (body != null) {
                request.content(new BytesContentProvider(body));
            }
            request.send();
            httpClient.stop();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
