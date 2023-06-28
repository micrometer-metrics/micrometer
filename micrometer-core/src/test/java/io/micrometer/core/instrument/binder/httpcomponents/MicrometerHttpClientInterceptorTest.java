/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MicrometerHttpClientInterceptor}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
@ExtendWith(WiremockResolver.class)
@Deprecated
class MicrometerHttpClientInterceptorTest {

    private MeterRegistry registry;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    }

    @Test
    void asyncRequest(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(any(anyUrl()));
        CloseableHttpAsyncClient client = asyncClient();
        client.start();
        HttpGet request = new HttpGet(server.baseUrl());

        Future<HttpResponse> future = client.execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(registry.get("httpcomponents.httpclient.request")
            .tag("method", "GET")
            .tag("status", "200")
            .tag("outcome", "SUCCESS")
            .timer()
            .count()).isEqualTo(1);

        client.close();
    }

    @Test
    void uriIsReadFromHttpHeader(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(any(anyUrl()));
        MicrometerHttpClientInterceptor interceptor = new MicrometerHttpClientInterceptor(registry, Tags.empty(), true);
        CloseableHttpAsyncClient client = asyncClient(interceptor);
        client.start();
        HttpGet request = new HttpGet(server.baseUrl());
        request.addHeader(DefaultUriMapper.URI_PATTERN_HEADER, "/some/pattern");

        Future<HttpResponse> future = client.execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(registry.get("httpcomponents.httpclient.request")
            .tag("uri", "/some/pattern")
            .tag("status", "200")
            .timer()
            .count()).isEqualTo(1);

        client.close();
    }

    private CloseableHttpAsyncClient asyncClient() {
        MicrometerHttpClientInterceptor interceptor = new MicrometerHttpClientInterceptor(registry,
                request -> request.getRequestLine().getUri(), Tags.empty(), true);
        return asyncClient(interceptor);
    }

    private CloseableHttpAsyncClient asyncClient(MicrometerHttpClientInterceptor interceptor) {
        return HttpAsyncClients.custom()
            .addInterceptorFirst(interceptor.getRequestInterceptor())
            .addInterceptorLast(interceptor.getResponseInterceptor())
            .build();
    }

}
