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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;

import javax.net.ssl.SSLSession;

/**
 * Jetty SSL/TLS handshake metrics.<br>
 * <br>
 * <p>
 * Usage example:
 *
 * <pre>{@code
 * MeterRegistry registry = ...;
 * Server server = new Server(0);
 * Connector connector = new ServerConnector(server);
 * connector.addBean(new JettySslHandshakeMetrics(registry));
 * server.setConnectors(new Connector[] { connector });
 * }</pre>
 *
 * Alternatively, configure on all connectors with
 * {@link JettySslHandshakeMetrics#addToAllConnectors(Server, MeterRegistry, Iterable)}.
 *
 * @author John Karp
 * @author Johnny Lim
 * @since 1.5.0
 */
public class JettySslHandshakeMetrics implements SslHandshakeListener {

    private static final String METER_NAME = "jetty.ssl.handshakes";

    private static final String DESCRIPTION = "SSL/TLS handshakes";

    private static final String TAG_RESULT = "result";

    private static final String TAG_PROTOCOL = "protocol";

    private static final String TAG_CIPHER_SUITE = "ciphersuite";

    private static final String TAG_VALUE_UNKNOWN = "unknown";

    private final MeterRegistry registry;

    private final Iterable<Tag> tags;

    private final Counter handshakesFailed;

    public JettySslHandshakeMetrics(MeterRegistry registry) {
        this(registry, Tags.empty());
    }

    public JettySslHandshakeMetrics(MeterRegistry registry, Iterable<Tag> tags) {
        this.registry = registry;
        this.tags = tags;

        this.handshakesFailed = Counter.builder(METER_NAME)
            .baseUnit(BaseUnits.EVENTS)
            .description(DESCRIPTION)
            .tag(TAG_RESULT, "failed")
            .tag(TAG_PROTOCOL, TAG_VALUE_UNKNOWN)
            .tag(TAG_CIPHER_SUITE, TAG_VALUE_UNKNOWN)
            .tags(tags)
            .register(registry);
    }

    /**
     * Create a {@code JettySslHandshakeMetrics} instance. {@link Connector#getName()}
     * will be used for {@literal connector.name} tag.
     * @param registry registry to use
     * @param connector connector to instrument
     * @since 1.8.0
     */
    public JettySslHandshakeMetrics(MeterRegistry registry, Connector connector) {
        this(registry, connector, Tags.empty());
    }

    /**
     * Create a {@code JettySslHandshakeMetrics} instance. {@link Connector#getName()}
     * will be used for {@literal connector.name} tag.
     * @param registry registry to use
     * @param connector connector to instrument
     * @param tags tags to add to metrics
     * @since 1.8.0
     */
    public JettySslHandshakeMetrics(MeterRegistry registry, Connector connector, Iterable<Tag> tags) {
        this(registry, getConnectorNameTag(connector).and(tags));
    }

    private static Tags getConnectorNameTag(Connector connector) {
        String name = connector.getName();
        return Tags.of("connector.name", name != null ? name : "unnamed");
    }

    @Override
    public void handshakeSucceeded(Event event) {
        SSLSession session = event.getSSLEngine().getSession();
        Counter.builder(METER_NAME)
            .baseUnit(BaseUnits.EVENTS)
            .description(DESCRIPTION)
            .tag(TAG_RESULT, "succeeded")
            .tag(TAG_PROTOCOL, session.getProtocol())
            .tag(TAG_CIPHER_SUITE, session.getCipherSuite())
            .tags(tags)
            .register(registry)
            .increment();
    }

    @Override
    public void handshakeFailed(Event event, Throwable failure) {
        handshakesFailed.increment();
    }

    public static void addToAllConnectors(Server server, MeterRegistry registry, Iterable<Tag> tags) {
        for (Connector connector : server.getConnectors()) {
            if (connector != null) {
                connector.addBean(new JettySslHandshakeMetrics(registry, connector, tags));
            }
        }
    }

    public static void addToAllConnectors(Server server, MeterRegistry registry) {
        addToAllConnectors(server, registry, Tags.empty());
    }

}
