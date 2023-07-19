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

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.eclipse.jetty.server.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JettySslHandshakeMetrics}.
 *
 * @author John Karp
 * @author Johnny Lim
 */
class JettySslHandshakeMetricsTest {

    private SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private JettySslHandshakeMetrics sslHandshakeMetrics;

    Iterable<Tag> tags = Tags.of("id", "0");

    SSLSession session = mock(SSLSession.class);

    SSLEngine engine = mock(SSLEngine.class);

    @BeforeEach
    void setup() {
        when(engine.getSession()).thenReturn(session);
    }

    @Test
    void handshakeFailed() {
        sslHandshakeMetrics = new JettySslHandshakeMetrics(registry, tags);
        SslHandshakeListener.Event event = new SslHandshakeListener.Event(engine);
        sslHandshakeMetrics.handshakeFailed(event, new javax.net.ssl.SSLHandshakeException(""));
        assertThat(registry.get("jetty.ssl.handshakes")
            .tags("id", "0", "protocol", "unknown", "ciphersuite", "unknown", "result", "failed")
            .counter()
            .count()).isEqualTo(1.0);
    }

    @Test
    void handshakeSucceeded() {
        sslHandshakeMetrics = new JettySslHandshakeMetrics(registry, tags);
        SslHandshakeListener.Event event = new SslHandshakeListener.Event(engine);
        when(session.getProtocol()).thenReturn("TLSv1.3");
        when(session.getCipherSuite()).thenReturn("RSA_XYZZY");
        sslHandshakeMetrics.handshakeSucceeded(event);
        assertThat(registry.get("jetty.ssl.handshakes")
            .tags("id", "0", "protocol", "TLSv1.3", "ciphersuite", "RSA_XYZZY", "result", "succeeded")
            .counter()
            .count()).isEqualTo(1.0);
    }

    @Test
    void connectorTagAdded() {
        Connector connector = mock(Connector.class);
        new JettySslHandshakeMetrics(registry, connector, tags);
        assertThatCode(() -> registry.get("jetty.ssl.handshakes")
            .tags("id", "0", "protocol", "unknown", "ciphersuite", "unknown", "result", "failed", "connector.name",
                    "unnamed")
            .counter()).doesNotThrowAnyException();
    }

    @Test
    void namedConnectorTagAdded() {
        Connector connector = mock(Connector.class);
        when(connector.getName()).thenReturn("my-connector");
        new JettySslHandshakeMetrics(registry, connector, tags);
        assertThatCode(() -> registry.get("jetty.ssl.handshakes")
            .tags("id", "0", "protocol", "unknown", "ciphersuite", "unknown", "result", "failed", "connector.name",
                    "my-connector")
            .counter()).doesNotThrowAnyException();
    }

}
