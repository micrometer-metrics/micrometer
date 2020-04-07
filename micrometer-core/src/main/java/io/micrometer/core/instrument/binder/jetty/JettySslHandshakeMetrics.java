/**
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
 * Jetty SSL/TLS handshake metrics.<br><br>
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
 * Alternatively, configure on all connectors with {@link JettySslHandshakeMetrics#addToAllConnectors(Server, MeterRegistry, Iterable)}.
 *
 */
public class JettySslHandshakeMetrics implements SslHandshakeListener {
    private final MeterRegistry registry;
    private final Iterable<Tag> tags;

    private final Counter handshakesFailed;

    public JettySslHandshakeMetrics(MeterRegistry registry) {
        this(registry, Tags.empty());
    }

    public JettySslHandshakeMetrics(MeterRegistry registry, Iterable<Tag> tags) {
        this.registry = registry;
        this.tags = tags;

        this.handshakesFailed = Counter.builder("jetty.ssl.handshakes")
                .baseUnit(BaseUnits.EVENTS)
                .description("SSL/TLS handshakes")
                .tags(Tags.concat(tags, "result", "failed"))
                .register(registry);
    }

    @Override
    public void handshakeSucceeded(Event event) {
        SSLSession session = event.getSSLEngine().getSession();
        Counter.builder("jetty.ssl.handshakes")
                .baseUnit(BaseUnits.EVENTS)
                .description("SSL/TLS handshakes")
                .tag("result", "succeeded")
                .tag("protocol", session.getProtocol())
                .tag("ciphersuite", session.getCipherSuite())
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
                connector.addBean(new JettySslHandshakeMetrics(registry, tags));
            }
        }
    }

    public static void addToAllConnectors(Server server, MeterRegistry registry) {
        addToAllConnectors(server, registry, Tags.empty());
    }
}
