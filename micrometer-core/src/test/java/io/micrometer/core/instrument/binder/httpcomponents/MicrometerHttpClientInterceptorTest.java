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

import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.*;

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

    @Test
    void responseInterceptorDoesNotThrowNpeIfRequestInterceptorNeverRan() {
        MicrometerHttpClientInterceptor interceptor = new MicrometerHttpClientInterceptor(registry, Tags.empty(), true);
        HttpContext context = new BasicHttpContext();
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        assertThatCode(() -> interceptor.getResponseInterceptor().process(response, context))
            .doesNotThrowAnyException();
    }

    @Test
    void whenConnectionResetByServer_noLeakOccurs(@WiremockResolver.Wiremock WireMockServer server) throws Exception {
        server.stubFor(any(anyUrl()).willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        CloseableHttpAsyncClient client = asyncClient();
        client.start();

        HttpContext context = new BasicHttpContext();
        WeakReference<HttpContext> weakContext = new WeakReference<>(context);

        Future<HttpResponse> future = client.execute(new HttpGet(server.baseUrl() + "/test"), context, null);
        try {
            future.get();
            fail("Expected exception");
        }
        catch (Exception e) {
            // Expected transport failure (connection reset), meaning
            // responseInterceptor is never called
        }

        // Release references to the context, future, etc., but keep client active!
        context = null;
        future = null;

        // Perform GC and assert that the HttpContext is garbage-collected while the
        // client is still active
        boolean collected = false;
        for (int i = 0; i < 50; i++) {
            System.gc();
            if (weakContext.get() == null) {
                collected = true;
                break;
            }
            Thread.sleep(50);
        }
        assertThat(collected).isTrue();

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
