/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.jetty12.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class JettyClientMetricsTest {

    protected SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    protected CountDownLatch singleRequestLatch = new CountDownLatch(1);

    protected HttpClient httpClient = new HttpClient();

    @BeforeEach
    void beforeEach() throws Exception {
        httpClient.setFollowRedirects(false);
        addInstrumentingListener();

        httpClient.addEventListener(new LifeCycle.Listener() {
            @Override
            public void lifeCycleStopped(LifeCycle event) {
                singleRequestLatch.countDown();
            }
        });

        httpClient.start();
    }

    protected void addInstrumentingListener() {
        httpClient.getRequestListeners()
            .addListener(JettyClientMetrics.builder(registry, (request, result) -> request.getURI().getPath()).build());
    }

    @Test
    void successfulHttpPostRequest(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/ok").willReturn(ok()));

        Request post = httpClient.POST("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/ok");
        post.body(new StringRequestContent("123456"));
        post.send();
        httpClient.stop();

        assertThat(singleRequestLatch.await(10, SECONDS)).isTrue();
        assertThat(registry.get("jetty.client.requests")
            .tag("outcome", "SUCCESS")
            .tag("status", "200")
            .tag("uri", "/ok")
            .tag("host", "localhost")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void successfulHttpGetRequest(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(get("/ok").willReturn(ok()));

        httpClient.GET("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/ok");
        httpClient.stop();

        assertThat(singleRequestLatch.await(10, SECONDS)).isTrue();
        assertThat(registry.get("jetty.client.requests")
            .tag("outcome", "SUCCESS")
            .tag("status", "200")
            .tag("uri", "/ok")
            .timer()
            .count()).isEqualTo(1);
        DistributionSummary requestSizeSummary = registry.get("jetty.client.request.size").summary();
        assertThat(requestSizeSummary.count()).isEqualTo(1);
        assertThat(requestSizeSummary.totalAmount()).isEqualTo(0);
    }

    @Test
    void requestSize(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/ok").willReturn(ok()));

        Request post = httpClient.POST("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/ok");
        post.body(new StringRequestContent("123456"));
        post.send();
        httpClient.stop();

        assertThat(singleRequestLatch.await(10, SECONDS)).isTrue();
        assertThat(registry.get("jetty.client.request.size")
            .tag("outcome", "SUCCESS")
            .tag("status", "200")
            .tag("uri", "/ok")
            .tag("host", "localhost")
            .summary()
            .totalAmount()).isEqualTo("123456".length());
    }

    @Test
    void serverError(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        stubFor(post("/error").willReturn(WireMock.serverError()));

        Request post = httpClient.POST("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/error");
        post.body(new StringRequestContent("123456"));
        post.send();
        httpClient.stop();

        assertThat(singleRequestLatch.await(10, SECONDS)).isTrue();
        assertThat(registry.get("jetty.client.requests")
            .tag("outcome", "SERVER_ERROR")
            .tag("status", "500")
            .tag("uri", "/error")
            .tag("host", "localhost")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void notFound(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
        Request post = httpClient.POST("http://localhost:" + wmRuntimeInfo.getHttpPort() + "/doesNotExist");
        post.body(new StringRequestContent("123456"));
        post.send();
        httpClient.stop();

        assertThat(singleRequestLatch.await(10, SECONDS)).isTrue();
        assertThat(registry.get("jetty.client.requests")
            .tag("outcome", "CLIENT_ERROR")
            .tag("status", "404")
            .tag("uri", "/doesNotExist")
            .tag("host", "localhost")
            .timer()
            .count()).isEqualTo(1);
    }

}
