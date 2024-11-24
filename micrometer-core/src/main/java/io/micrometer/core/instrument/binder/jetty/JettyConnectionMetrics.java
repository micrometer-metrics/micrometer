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

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowMax;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.ByteBuffer;

import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.NetworkTrafficListener;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

import java.util.HashMap;
import java.util.Map;

/**
 * Jetty connection metrics.<br>
 * <br>
 * <p>
 * Usage example:
 *
 * <pre>{@code
 * MeterRegistry registry = ...;
 * Server server = new Server(0);
 * Connector connector = new ServerConnector(server);
 * connector.addBean(new JettyConnectionMetrics(registry));
 * server.setConnectors(new Connector[] { connector });
 * }</pre>
 *
 * Alternatively, configure on all server connectors with
 * {@link JettyConnectionMetrics#addToAllConnectors(Server, MeterRegistry, Iterable)}.
 *
 * @author Jon Schneider
 * @since 1.4.0
 */
public class JettyConnectionMetrics extends AbstractLifeCycle implements Connection.Listener, NetworkTrafficListener {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(JettyConnectionMetrics.class);

    private final MeterRegistry registry;

    private final Iterable<Tag> tags;

    private final Object connectionSamplesLock = new Object();

    private final Map<Connection, Timer.Sample> connectionSamples = new HashMap<>();

    private final Counter messagesIn;

    private final Counter messagesOut;

    private final DistributionSummary bytesIn;

    private final DistributionSummary bytesOut;

    private final TimeWindowMax maxConnections;

    public JettyConnectionMetrics(MeterRegistry registry) {
        this(registry, Tags.empty());
    }

    public JettyConnectionMetrics(MeterRegistry registry, Iterable<Tag> tags) {
        this.registry = registry;
        this.tags = tags;

        this.messagesIn = Counter.builder("jetty.connections.messages.in")
            .baseUnit(BaseUnits.MESSAGES)
            .description("Messages received by tracked connections")
            .tags(tags)
            .register(registry);

        this.messagesOut = Counter.builder("jetty.connections.messages.out")
            .baseUnit(BaseUnits.MESSAGES)
            .description("Messages sent by tracked connections")
            .tags(tags)
            .register(registry);

        this.bytesIn = DistributionSummary.builder("jetty.connections.bytes.in")
            .baseUnit(BaseUnits.BYTES)
            .description("Bytes received by tracked connections")
            .tags(tags)
            .register(registry);

        this.bytesOut = DistributionSummary.builder("jetty.connections.bytes.out")
            .baseUnit(BaseUnits.BYTES)
            .description("Bytes sent by tracked connections")
            .tags(tags)
            .register(registry);

        this.maxConnections = new TimeWindowMax(registry.config().clock(), DistributionStatisticConfig.DEFAULT);

        Gauge.builder("jetty.connections.max", this, jcm -> jcm.maxConnections.poll())
            .strongReference(true)
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The maximum number of observed connections over a rolling 2-minute interval")
            .tags(tags)
            .register(registry);

        Gauge.builder("jetty.connections.current", this, jcm -> jcm.connectionSamples.size())
            .strongReference(true)
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The current number of open Jetty connections")
            .tags(tags)
            .register(registry);
    }

    /**
     * Create a {@code JettyConnectionMetrics} instance. {@link Connector#getName()} will
     * be used for {@literal connector.name} tag.
     * @param registry registry to use
     * @param connector connector to instrument
     * @since 1.8.0
     */
    public JettyConnectionMetrics(MeterRegistry registry, Connector connector) {
        this(registry, connector, Tags.empty());
    }

    /**
     * Create a {@code JettyConnectionMetrics} instance. {@link Connector#getName()} will
     * be used for {@literal connector.name} tag.
     * @param registry registry to use
     * @param connector connector to instrument
     * @param tags tags to add to metrics
     * @since 1.8.0
     */
    public JettyConnectionMetrics(MeterRegistry registry, Connector connector, Iterable<Tag> tags) {
        this(registry, getConnectorNameTag(connector).and(tags));
    }

    private static Tags getConnectorNameTag(Connector connector) {
        String name = connector.getName();
        return Tags.of("connector.name", name != null ? name : "unnamed");
    }

    @Override
    public void onOpened(Connection connection) {
        Timer.Sample started = Timer.start(registry);
        synchronized (connectionSamplesLock) {
            connectionSamples.put(connection, started);
            maxConnections.record(connectionSamples.size());
        }
    }

    @Override
    public void onClosed(Connection connection) {
        Timer.Sample sample;
        synchronized (connectionSamplesLock) {
            sample = connectionSamples.remove(connection);
        }

        if (sample != null) {
            String type = "UNKNOWN";
            if (connection.getClass().getName().contains("server")) {
                type = "server";
            }
            else if (connection.getClass().getName().contains("client")) {
                type = "client";
            }
            sample.stop(Timer.builder("jetty.connections.request")
                .description("Jetty client or server requests")
                .tag("type", type)
                .tags(tags)
                .register(registry));
        }
        messagesIn.increment(connection.getMessagesIn());
        messagesOut.increment(connection.getMessagesOut());
    }

    @Override
    public void incoming(Socket socket, ByteBuffer bytes) {
        bytesIn.record(bytes.limit());
    }

    @Override
    public void outgoing(Socket socket, ByteBuffer bytes) {
        bytesOut.record(bytes.limit());
    }

    /**
     * Configures metrics instrumentation on all the {@link Server}'s {@link Connector}s.
     * @param server apply to this server's connectors
     * @param registry register metrics to this registry
     * @param tags add these tags as additional tags on metrics registered via this
     */
    public static void addToAllConnectors(Server server, MeterRegistry registry, Iterable<Tag> tags) {
        for (Connector connector : server.getConnectors()) {
            if (connector != null) {
                JettyConnectionMetrics metrics = new JettyConnectionMetrics(registry, connector, tags);
                connector.addBean(metrics);
                if (connector instanceof NetworkTrafficServerConnector) {
                    NetworkTrafficServerConnector networkTrafficServerConnector = (NetworkTrafficServerConnector) connector;
                    Method setNetworkTrafficListenerMethod = getNetworkTrafficListenerMethod(
                            networkTrafficServerConnector);
                    if (setNetworkTrafficListenerMethod != null) {
                        try {
                            setNetworkTrafficListenerMethod.invoke(networkTrafficServerConnector, metrics);
                        }
                        catch (IllegalAccessException | InvocationTargetException e) {
                            logger.debug("Unable to set network traffic listener on connector " + connector, e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Configures metrics instrumentation on all the {@link Server}'s {@link Connector}s.
     * @param server apply to this server's connectors
     * @param registry register metrics to this registry
     */
    public static void addToAllConnectors(Server server, MeterRegistry registry) {
        addToAllConnectors(server, registry, Tags.empty());
    }

    @Nullable
    private static Method getNetworkTrafficListenerMethod(NetworkTrafficServerConnector networkTrafficServerConnector) {
        Method method = null;
        try {
            // Jetty 9 method
            method = networkTrafficServerConnector.getClass()
                .getMethod("addNetworkTrafficListener", NetworkTrafficListener.class);
        }
        catch (NoSuchMethodException ignore) {
        }
        if (method != null)
            return method;
        try {
            // Jetty 12 method
            method = networkTrafficServerConnector.getClass()
                .getMethod("setNetworkTrafficListener", NetworkTrafficListener.class);
        }
        catch (NoSuchMethodException ignore) {
        }
        return method;
    }

}
