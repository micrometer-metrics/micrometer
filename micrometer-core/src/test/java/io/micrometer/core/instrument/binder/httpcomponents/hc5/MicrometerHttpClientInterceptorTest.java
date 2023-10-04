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
package io.micrometer.core.instrument.binder.httpcomponents.hc5;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
@SuppressWarnings("deprecation")
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
        SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl()).build();

        Future<SimpleHttpResponse> future = client.execute(request, null);
        HttpResponse response = future.get();

        assertThat(response.getCode()).isEqualTo(200);
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
        SimpleHttpRequest request = SimpleRequestBuilder.get(server.baseUrl()).build();
        request.addHeader(DefaultUriMapper.URI_PATTERN_HEADER, "/some/pattern");

        Future<SimpleHttpResponse> future = client.execute(request, null);
        HttpResponse response = future.get(1, TimeUnit.SECONDS);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(registry.get("httpcomponents.httpclient.request")
            .tag("uri", "/some/pattern")
            .tag("status", "200")
            .timer()
            .count()).isEqualTo(1);

        client.close();
    }

    private CloseableHttpAsyncClient asyncClient() {
        MicrometerHttpClientInterceptor interceptor = new MicrometerHttpClientInterceptor(registry,
                HttpRequest::getRequestUri, Tags.empty(), true);
        return asyncClient(interceptor);
    }

    private CloseableHttpAsyncClient asyncClient(MicrometerHttpClientInterceptor interceptor) {
        return HttpAsyncClients.custom()
            .addRequestInterceptorFirst(interceptor.getRequestInterceptor())
            .addResponseInterceptorLast(interceptor.getResponseInterceptor())
            .build();
    }

}
