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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class JettyClientMetricsTest {

    protected SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private Server server = new Server(0);

    protected ServerConnector connector = new ServerConnector(server);

    protected CountDownLatch singleRequestLatch = new CountDownLatch(1);

    protected HttpClient httpClient = new HttpClient();

    @BeforeEach
    void beforeEach() throws Exception {
        server.insertHandler(new HandlerWrapper() {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request,
                    HttpServletResponse response) {
                switch (request.getPathInfo()) {
                    case "/errorUnchecked":
                        throw new RuntimeException("big boom");
                    case "/error":
                        response.setStatus(500);
                    case "/ok":
                        baseRequest.setHandled(true);
                }
            }
        });
        server.setConnectors(new Connector[] { connector });
        server.start();

        httpClient.setFollowRedirects(false);
        // noinspection deprecation
        httpClient.getRequestListeners()
            .add(JettyClientMetrics.builder(registry, result -> result.getRequest().getURI().getPath()).build());

        httpClient.addLifeCycleListener(new LifeCycle.Listener() {
            @Override
            public void lifeCycleStopped(LifeCycle event) {
                singleRequestLatch.countDown();
            }
        });

        httpClient.start();

    }

    @AfterEach
    void teardown() throws Exception {
        if (server.isRunning()) {
            server.stop();
        }
    }

    @Test
    void successfulHttpPostRequest() throws Exception {
        Request post = httpClient.POST("http://localhost:" + connector.getLocalPort() + "/ok");
        post.content(new StringContentProvider("123456"));
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
    void successfulHttpGetRequest() throws Exception {
        httpClient.GET("http://localhost:" + connector.getLocalPort() + "/ok");
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
    void requestSize() throws Exception {
        Request post = httpClient.POST("http://localhost:" + connector.getLocalPort() + "/ok");
        post.content(new StringContentProvider("123456"));
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
    void serverError() throws Exception {
        Request post = httpClient.POST("http://localhost:" + connector.getLocalPort() + "/error");
        post.content(new StringContentProvider("123456"));
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
    void handlerWrapperUncheckedException() throws Exception {
        Request post = httpClient.POST("http://localhost:" + connector.getLocalPort() + "/errorUnchecked");
        post.content(new StringContentProvider("123456"));
        post.send();
        httpClient.stop();

        assertThat(singleRequestLatch.await(10, SECONDS)).isTrue();
        assertThat(registry.get("jetty.client.requests")
            .tag("outcome", "SERVER_ERROR")
            .tag("status", "500")
            .tag("uri", "/errorUnchecked")
            .tag("host", "localhost")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void notFound() throws Exception {
        Request post = httpClient.POST("http://localhost:" + connector.getLocalPort() + "/doesNotExist");
        post.content(new StringContentProvider("123456"));
        post.send();
        httpClient.stop();

        assertThat(singleRequestLatch.await(10, SECONDS)).isTrue();
        assertThat(registry.get("jetty.client.requests")
            .tag("outcome", "CLIENT_ERROR")
            .tag("status", "404")
            .tag("uri", "NOT_FOUND")
            .tag("host", "localhost")
            .timer()
            .count()).isEqualTo(1);
    }

}
