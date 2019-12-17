/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnectionStatistics;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JettyConnectionMetricsTest {

    private SimpleMeterRegistry registry;
    private ServerConnector connector;
    private Server server;
    private CloseableHttpClient client;

    @BeforeEach
    void setup() throws Exception {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

        ServerConnectionStatistics serverConnectionStatistics = new ServerConnectionStatistics();
        JettyConnectionMetrics connectionMetrics =
                new JettyConnectionMetrics(serverConnectionStatistics, singletonList(Tag.of("protocol", "http")));
        connectionMetrics.bindTo(registry);

        server = new Server(0);
        connector = new ServerConnector(server);
        connector.addBean(serverConnectionStatistics);
        server.setConnectors(new Connector[] { connector });
        server.start();

        client = HttpClients.createDefault();
    }

    @AfterEach
    void teardown() throws Exception {
        if (server.isRunning()) {
            server.stop();
        }
    }

    @Test
    void contributesConnectorMetrics() throws Exception {
        String url = getBaseUrl();
        HttpPost post = new HttpPost(url);
        post.setEntity(new StringEntity("some blah whatever text"));
        try (CloseableHttpResponse response = client.execute(post)) {
            assertThat(registry.get("jetty.connector.connections.max").gauge().value()).isEqualTo(1.0);
            assertThat(registry.get("jetty.connector.connections.current").gauge().value()).isEqualTo(1.0);
            assertThat(registry.get("jetty.connector.connections.total").gauge().value()).isEqualTo(1.0);
        }

        CountDownLatch latch = new CountDownLatch(1);
        connector.addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener() {
            @Override
            public void lifeCycleStopped(LifeCycle event) {
                latch.countDown();
            }
        });
        // Convenient way to get Jetty to flush its connections, which is required to update the sent/received bytes metrics
        server.stop();

        assertTrue(latch.await(10, SECONDS));

        assertThat(registry.get("jetty.connector.bytesSent").functionCounter().count()).isGreaterThan(0.0);
        assertThat(registry.get("jetty.connector.bytesReceived").functionCounter().count()).isGreaterThan(0.0);
    }

    private String getBaseUrl() {
        return "http://localhost:" + connector.getLocalPort();
    }
}
