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
package io.micrometer.core.instrument.binder.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionId;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerId;
import com.mongodb.event.*;
import io.micrometer.common.lang.NonNull;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link MongoMetricsConnectionPoolListener}.
 *
 * @author Christophe Bornet
 * @author Jonatan Ivanov
 */
class MongoMetricsConnectionPoolListenerTest extends AbstractMongoDbTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void shouldCreatePoolMetrics() {
        AtomicReference<String> clusterId = new AtomicReference<>();
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyToConnectionPoolSettings(builder -> builder.minSize(2)
                .addConnectionPoolListener(new MongoMetricsConnectionPoolListener(registry)))
            .applyToClusterSettings(builder -> builder.hosts(singletonList(new ServerAddress(host, port)))
                .addClusterListener(new ClusterListener() {
                    @Override
                    public void clusterOpening(@NonNull ClusterOpeningEvent event) {
                        clusterId.set(event.getClusterId().getValue());
                    }
                }))
            .build();
        MongoClient mongo = MongoClients.create(settings);

        mongo.getDatabase("test").createCollection("testCol");

        Tags tags = Tags.of("cluster.id", clusterId.get(), "server.address", String.format("%s:%s", host, port));

        assertThat(registry.get("mongodb.driver.pool.size").tags(tags).gauge().value()).isEqualTo(2);
        assertThat(registry.get("mongodb.driver.pool.checkedout").gauge().value()).isZero();
        assertThat(registry.get("mongodb.driver.pool.waitqueuesize").gauge().value()).isZero();

        mongo.close();

        assertThat(registry.find("mongodb.driver.pool.size").tags(tags).gauge())
            .describedAs("metrics should be removed when the connection pool is closed")
            .isNull();
    }

    @Test
    void shouldCreatePoolMetricsWithCustomTags() {
        AtomicReference<String> clusterId = new AtomicReference<>();
        // tag::setup[]
        MeterRegistry registry = new SimpleMeterRegistry();
        MongoMetricsConnectionPoolListener connectionPoolListener = new MongoMetricsConnectionPoolListener(registry,
                e -> Tags.of("cluster.id", e.getServerId().getClusterId().getValue(), "server.address",
                        e.getServerId().getAddress().toString(), "my.custom.connection.pool.identifier", "custom"));
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyToConnectionPoolSettings(
                    builder -> builder.minSize(2).addConnectionPoolListener(connectionPoolListener))
            .applyToClusterSettings(builder -> builder.hosts(singletonList(new ServerAddress(host, port)))
                .addClusterListener(new ClusterListener() {
                    @Override
                    public void clusterOpening(@NonNull ClusterOpeningEvent event) {
                        clusterId.set(event.getClusterId().getValue());
                    }
                }))
            .build();
        MongoClient mongo = MongoClients.create(settings);
        // end::setup[]

        // tag::example[]
        mongo.getDatabase("test").createCollection("testCol");

        Tags tags = Tags.of("cluster.id", clusterId.get(), "server.address", String.format("%s:%s", host, port),
                "my.custom.connection.pool.identifier", "custom");

        assertThat(registry.get("mongodb.driver.pool.size").tags(tags).gauge().value()).isEqualTo(2);
        assertThat(registry.get("mongodb.driver.pool.checkedout").gauge().value()).isZero();
        assertThat(registry.get("mongodb.driver.pool.checkoutfailed").counter().count()).isZero();
        assertThat(registry.get("mongodb.driver.pool.waitqueuesize").gauge().value()).isZero();

        mongo.close();
        // end::example[]

        assertThat(registry.find("mongodb.driver.pool.size").tags(tags).gauge())
            .describedAs("metrics should be removed when the connection pool is closed")
            .isNull();
    }

    @Test
    void shouldIncrementCheckoutFailedCount() {
        ServerId serverId = new ServerId(new ClusterId(), new ServerAddress(host, port));
        MongoMetricsConnectionPoolListener listener = new MongoMetricsConnectionPoolListener(registry);
        listener
            .connectionPoolCreated(new ConnectionPoolCreatedEvent(serverId, ConnectionPoolSettings.builder().build()));

        // start a connection checkout
        listener.connectionCheckOutStarted(new ConnectionCheckOutStartedEvent(serverId, -1));
        assertThat(registry.get("mongodb.driver.pool.waitqueuesize").gauge().value()).isEqualTo(1);
        assertThat(registry.get("mongodb.driver.pool.checkoutfailed").counter().count()).isZero();

        // let the connection checkout fail, simulating a timeout
        ConnectionCheckOutFailedEvent.Reason reason = ConnectionCheckOutFailedEvent.Reason.TIMEOUT;
        long elapsedTimeNanos = TimeUnit.SECONDS.toNanos(120);
        ConnectionCheckOutFailedEvent checkOutFailedEvent = new ConnectionCheckOutFailedEvent(serverId, -1, reason,
                elapsedTimeNanos);
        listener.connectionCheckOutFailed(checkOutFailedEvent);
        assertThat(registry.get("mongodb.driver.pool.waitqueuesize").gauge().value()).isZero();
        assertThat(registry.get("mongodb.driver.pool.checkoutfailed").counter().count()).isEqualTo(1);
    }

    @Issue("#2384")
    @Test
    void whenConnectionCheckedInAfterPoolClose_thenNoExceptionThrown() {
        ServerId serverId = new ServerId(new ClusterId(), new ServerAddress(host, port));
        ConnectionId connectionId = new ConnectionId(serverId);
        MongoMetricsConnectionPoolListener listener = new MongoMetricsConnectionPoolListener(registry);
        listener
            .connectionPoolCreated(new ConnectionPoolCreatedEvent(serverId, ConnectionPoolSettings.builder().build()));
        listener.connectionCheckedOut(new ConnectionCheckedOutEvent(connectionId, -1, 0));
        listener.connectionPoolClosed(new ConnectionPoolClosedEvent(serverId));
        assertThatCode(() -> listener.connectionCheckedIn(new ConnectionCheckedInEvent(connectionId, -1)))
            .doesNotThrowAnyException();
    }

}
