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

import io.micrometer.core.instrument.binder.httpcomponents.DefaultUriMapper;
import io.micrometer.core.instrument.binder.httpcomponents.MicrometerHttpRequestExecutor;
import io.micrometer.core.lang.Nullable;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

class ApacheHttpClientTimingInstrumentationVerificationTests extends HttpClientTimingInstrumentationVerificationTests {

    private final HttpClient httpClient = HttpClientBuilder.create()
        .setRequestExecutor(MicrometerHttpRequestExecutor.builder(getRegistry()).build())
        .build();

    @Override
    protected String timerName() {
        return "httpcomponents.httpclient.request";
    }

    @Override
    protected void sendHttpRequest(HttpMethod method, @Nullable byte[] body, URI baseUri, String templatedPath,
            String... pathVariables) {
        try {
            EntityUtils.consume(
                    httpClient.execute(makeRequest(method, body, baseUri, templatedPath, pathVariables)).getEntity());
        }
        catch (IOException e) {
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
