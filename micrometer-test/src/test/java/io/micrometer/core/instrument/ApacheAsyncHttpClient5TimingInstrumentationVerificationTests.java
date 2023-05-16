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
import io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientMetricsBinder;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ContentType;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class ApacheAsyncHttpClient5TimingInstrumentationVerificationTests
        extends HttpClientTimingInstrumentationVerificationTests<CloseableHttpAsyncClient> {

    @Override
    protected CloseableHttpAsyncClient clientInstrumentedWithMetrics() {

        HttpAsyncClientBuilder clientBuilder = HttpAsyncClients.custom();

        ApacheHttpClientMetricsBinder.builder(getRegistry()).build().instrument(clientBuilder);

        CloseableHttpAsyncClient client = clientBuilder.build();
        client.start();
        return client;
    }

    @Nullable
    @Override
    protected CloseableHttpAsyncClient clientInstrumentedWithObservations() {
        return null;
    }

    @Override
    protected String timerName() {
        return "httpcomponents.httpclient.request";
    }

    @Override
    protected void sendHttpRequest(CloseableHttpAsyncClient instrumentedClient, HttpMethod method,
            @Nullable byte[] body, URI baseUri, String templatedPath, String... pathVariables) {
        try {
            Future<SimpleHttpResponse> future = instrumentedClient
                .execute(makeRequest(method, body, baseUri, templatedPath, pathVariables), null);
            future.get();
        }
        catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private SimpleHttpRequest makeRequest(HttpMethod method, @Nullable byte[] body, URI baseUri, String templatedPath,
            String... pathVariables) {
        SimpleRequestBuilder builder = SimpleRequestBuilder.create(method.name());
        if (body != null) {
            builder.setBody(body, ContentType.TEXT_PLAIN);
        }
        builder.setUri(URI.create(baseUri + substitutePathVariables(templatedPath, pathVariables)));
        builder.setHeader(DefaultUriMapper.URI_PATTERN_HEADER, templatedPath);
        return builder.build();
    }

}
