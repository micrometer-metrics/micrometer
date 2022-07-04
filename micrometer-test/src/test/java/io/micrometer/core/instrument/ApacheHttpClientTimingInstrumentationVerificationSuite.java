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

import io.micrometer.core.instrument.binder.httpcomponents.MicrometerHttpRequestExecutor;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;

class ApacheHttpClientTimingInstrumentationVerificationSuite extends HttpClientTimingInstrumentationVerificationSuite {

    private final HttpClient httpClient = HttpClientBuilder.create()
            .setRequestExecutor(MicrometerHttpRequestExecutor.builder(getRegistry()).build()).build();

    @Override
    protected String timerName() {
        return "httpcomponents.httpclient.request";
    }

    @Override
    void sendHttpRequest(HttpMethod method, URI baseUri, String templatedPath, String... pathVariables) {
        try {
            EntityUtils
                    .consume(
                            httpClient
                                    .execute(makeRequest(method,
                                            URI.create(
                                                    baseUri + substitutePathVariables(templatedPath, pathVariables))))
                                    .getEntity());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpUriRequest makeRequest(HttpMethod method, URI uri) {
        switch (method) {
            case POST:
                return new HttpPost(uri);
            default:
                return new HttpGet(uri);
        }
    }

}
