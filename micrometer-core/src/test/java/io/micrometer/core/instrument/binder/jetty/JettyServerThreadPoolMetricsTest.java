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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Manabu Matsuzaki
 */
class JettyServerThreadPoolMetricsTest {

    private SimpleMeterRegistry registry;

    private Server server;

    private JettyServerThreadPoolMetrics threadPoolMetrics;

    @BeforeEach
    void setup() throws Exception {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

        Iterable<Tag> tags = Collections.singletonList(Tag.of("id", "0"));
        QueuedThreadPool threadPool = new QueuedThreadPool();

        threadPool.setMinThreads(32);
        threadPool.setMaxThreads(100);
        server = new Server(threadPool);
        ServerConnector connector = new ServerConnector(server);
        server.setConnectors(new Connector[] { connector });
        server.start();
        threadPoolMetrics = new JettyServerThreadPoolMetrics(threadPool, tags);
        threadPoolMetrics.bindTo(registry);
    }

    @AfterEach
    void teardown() throws Exception {
        server.stop();
    }

    @Test
    void threadMetrics() throws Exception {
        assertThat(registry.get("jetty.threads.config.min").gauge().value()).isEqualTo(32.0);
        assertThat(registry.get("jetty.threads.config.max").gauge().value()).isEqualTo(100.0);
        assertThat(registry.get("jetty.threads.current").gauge().value()).isNotEqualTo(0.0);
        assertThat(registry.get("jetty.threads.busy").gauge().value()).isGreaterThanOrEqualTo(0.0);

        threadPoolMetrics.close();

        assertThat(registry.getMeters()).as("Meters are removed after close").isEmpty();
    }

}
