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
import io.micrometer.core.instrument.binder.httpcomponents.hc5.*;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

class ApacheHttpClient5TimingInstrumentationVerificationTests
        extends HttpClientTimingInstrumentationVerificationTests<CloseableHttpClient> {

    private static final HttpClientResponseHandler<ClassicHttpResponse> NOOP_RESPONSE_HANDLER = (response) -> response;

    @Override
    @SuppressWarnings("deprecation")
    protected CloseableHttpClient clientInstrumentedWithMetrics() {
        return HttpClientBuilder.create()
            .setRequestExecutor(MicrometerHttpRequestExecutor.builder(getRegistry()).build())
            .build();
    }

    @Nullable
    @Override
    protected CloseableHttpClient clientInstrumentedWithObservations() {
        return HttpClientBuilder.create()
            .addExecInterceptorFirst("micrometer", new ObservationExecChainHandler(getObservationRegistry()))
            .build();
    }

    @Override
    protected String timerName() {
        return "httpcomponents.httpclient.request";
    }

    @Override
    protected ObservationDocumentation observationDocumentation() {
        return ApacheHttpClientObservationDocumentation.DEFAULT;
    }

    @Override
    protected void sendHttpRequest(CloseableHttpClient instrumentedClient, HttpMethod method, @Nullable byte[] body,
            URI baseUri, String templatedPath, String... pathVariables) {
        try {
            EntityUtils.consume(instrumentedClient
                .execute(makeRequest(method, body, baseUri, templatedPath, pathVariables), NOOP_RESPONSE_HANDLER)
                .getEntity());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("deprecation")
    private HttpUriRequest makeRequest(HttpMethod method, @Nullable byte[] body, URI baseUri, String templatedPath,
            String... pathVariables) {
        HttpUriRequestBase request = new HttpUriRequestBase(method.name(),
                URI.create(baseUri + substitutePathVariables(templatedPath, pathVariables)));
        if (body != null) {
            BasicHttpEntity entity = new BasicHttpEntity(new ByteArrayInputStream(body), ContentType.TEXT_PLAIN);
            request.setEntity(entity);
        }
        request.setHeader(DefaultUriMapper.URI_PATTERN_HEADER, templatedPath);
        return request;
    }

}
