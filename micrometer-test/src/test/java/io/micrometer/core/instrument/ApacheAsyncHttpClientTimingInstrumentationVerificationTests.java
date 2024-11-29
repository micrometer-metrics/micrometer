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
package io.micrometer.core.instrument;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.binder.httpcomponents.DefaultUriMapper;
import io.micrometer.core.instrument.binder.httpcomponents.MicrometerHttpClientInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Deprecated
class ApacheAsyncHttpClientTimingInstrumentationVerificationTests
        extends HttpClientTimingInstrumentationVerificationTests<CloseableHttpAsyncClient> {

    @Override
    protected CloseableHttpAsyncClient clientInstrumentedWithMetrics() {
        MicrometerHttpClientInterceptor interceptor = new MicrometerHttpClientInterceptor(getRegistry(), Tags.empty(),
                true);
        CloseableHttpAsyncClient client = HttpAsyncClients.custom()
            .addInterceptorFirst(interceptor.getRequestInterceptor())
            .addInterceptorLast(interceptor.getResponseInterceptor())
            .build();
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
            Future<HttpResponse> future = instrumentedClient
                .execute(makeRequest(method, body, baseUri, templatedPath, pathVariables), null);
            future.get();
        }
        catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpUriRequest makeRequest(HttpMethod method, @Nullable byte[] body, URI baseUri, String templatedPath,
            String... pathVariables) {
        HttpEntityEnclosingRequestBase request = new HttpEntityEnclosingRequestBase() {
            @Override
            public String getMethod() {
                return method.name();
            }
        };
        if (body != null) {
            BasicHttpEntity entity = new BasicHttpEntity();
            entity.setContent(new ByteArrayInputStream(body));
            request.setEntity(entity);
        }
        request.setURI(URI.create(baseUri + substitutePathVariables(templatedPath, pathVariables)));
        request.setHeader(DefaultUriMapper.URI_PATTERN_HEADER, templatedPath);
        return request;
    }

}
