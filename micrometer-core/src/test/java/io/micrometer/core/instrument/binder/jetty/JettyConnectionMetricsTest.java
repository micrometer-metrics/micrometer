/*
 * Copyright 2019 VMware, Inc.
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

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JettyConnectionMetrics} with Jetty 9.
 */
class JettyConnectionMetricsTest {

    private SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private Server server = new Server(0);

    private NetworkTrafficServerConnector connector = new NetworkTrafficServerConnector(server);

    private CloseableHttpClient client = HttpClients.createDefault();

    void setup(boolean instrumentServer) throws Exception {
        if (instrumentServer) {
            JettyConnectionMetrics metrics = new JettyConnectionMetrics(registry);
            connector.addBean(metrics);
            connector.addNetworkTrafficListener(metrics);
        }
        server.setConnectors(new Connector[] { connector });
        server.start();
    }

    @AfterEach
    void teardown() throws Exception {
        if (server.isRunning()) {
            server.stop();
        }
    }

    @Test
    void directServerConnectorInstrumentation() throws Exception {
        setup(true);
        contributesServerConnectorMetrics();
    }

    @Test
    void addToAllConnectorsInstrumentation() throws Exception {
        server.setConnectors(new Connector[] { connector });
        JettyConnectionMetrics.addToAllConnectors(server, registry);
        server.start();

        contributesServerConnectorMetrics();
    }

    void contributesServerConnectorMetrics() throws Exception {
        HttpPost post = new HttpPost("http://localhost:" + connector.getLocalPort());
        post.setEntity(new StringEntity("123456"));

        try (CloseableHttpResponse ignored = client.execute(post)) {
            try (CloseableHttpResponse ignored2 = client.execute(post)) {
                assertThat(registry.get("jetty.connections.current").gauge().value()).isEqualTo(2.0);
                assertThat(registry.get("jetty.connections.max").gauge().value()).isEqualTo(2.0);
            }
        }

        CountDownLatch latch = new CountDownLatch(1);
        connector.addLifeCycleListener(new LifeCycle.Listener() {
            @Override
            public void lifeCycleStopped(LifeCycle event) {
                latch.countDown();
            }
        });
        // Convenient way to get Jetty to flush its connections, which is required to
        // update the sent/received bytes metrics
        server.stop();

        assertThat(latch.await(10, SECONDS)).isTrue();
        assertThat(registry.get("jetty.connections.max").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("jetty.connections.request").tag("type", "server").timer().count()).isEqualTo(2);
        assertThat(registry.get("jetty.connections.bytes.in").summary().totalAmount()).isGreaterThan(1);
    }

    @Test
    void contributesClientConnectorMetrics() throws Exception {
        setup(false);
        HttpClient httpClient = new HttpClient();
        httpClient.setFollowRedirects(false);
        httpClient.addBean(new JettyConnectionMetrics(registry));

        CountDownLatch latch = new CountDownLatch(1);
        httpClient.addLifeCycleListener(new LifeCycle.Listener() {
            @Override
            public void lifeCycleStopped(LifeCycle event) {
                latch.countDown();
            }
        });

        httpClient.start();

        Request post = httpClient.POST("http://localhost:" + connector.getLocalPort());
        post.content(new StringContentProvider("123456"));
        post.send();
        httpClient.stop();

        assertThat(latch.await(10, SECONDS)).isTrue();
        assertThat(registry.get("jetty.connections.max").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("jetty.connections.request").tag("type", "client").timer().count()).isEqualTo(1);
    }

    @Test
    void passingConnectorAddsConnectorNameTag() {
        new JettyConnectionMetrics(registry, connector);

        assertThat(registry.get("jetty.connections.messages.in").counter().getId().getTag("connector.name"))
            .isEqualTo("unnamed");
    }

    @Test
    void namedConnectorsGetTaggedWithName() {
        connector.setName("super-fast-connector");
        new JettyConnectionMetrics(registry, connector);

        assertThat(registry.get("jetty.connections.messages.in").counter().getId().getTag("connector.name"))
            .isEqualTo("super-fast-connector");
    }

}
