/**
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.binder.mongodb;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionId;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerId;
import com.mongodb.event.*;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link MongoMetricsConnectionPoolListener}.
 *
 * @author Christophe Bornet
 */
class MongoMetricsConnectionPoolListenerTest extends AbstractMongoDbTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void shouldCreatePoolMetrics() {
        AtomicReference<String> clusterId = new AtomicReference<>();
        MongoClientOptions options = MongoClientOptions.builder()
                .minConnectionsPerHost(2)
                .addConnectionPoolListener(new MongoMetricsConnectionPoolListener(registry))
                .addClusterListener(new ClusterListenerAdapter() {
                    @Override
                    public void clusterOpening(ClusterOpeningEvent event) {
                        clusterId.set(event.getClusterId().getValue());
                    }
                })
                .build();
        MongoClient mongo = new MongoClient(new ServerAddress(HOST, port), options);

        mongo.getDatabase("test")
                .createCollection("testCol");

        Tags tags = Tags.of(
                "cluster.id", clusterId.get(),
                "server.address", String.format("%s:%s", HOST, port)
        );

        assertThat(registry.get("mongodb.driver.pool.size").tags(tags).gauge().value()).isEqualTo(2);
        assertThat(registry.get("mongodb.driver.pool.checkedout").gauge().value()).isZero();
        assertThat(registry.get("mongodb.driver.pool.waitqueuesize").gauge().value()).isZero();

        mongo.close();

        assertThat(registry.find("mongodb.driver.pool.size").tags(tags).gauge())
                .describedAs("metrics should be removed when the connection pool is closed")
                .isNull();
    }

    @Test
    @Issue("#2384")
    void whenConnectionCheckedInAfterPoolClose_thenNoExceptionThrown() {
        ServerId serverId = new ServerId(new ClusterId(), new ServerAddress(HOST, port));
        ConnectionId connectionId = new ConnectionId(serverId);
        MongoMetricsConnectionPoolListener listener = new MongoMetricsConnectionPoolListener(registry);
        listener.connectionPoolOpened(new ConnectionPoolOpenedEvent(serverId, ConnectionPoolSettings.builder().build()));
        listener.connectionCheckedOut(new ConnectionCheckedOutEvent(connectionId));
        listener.connectionPoolClosed(new ConnectionPoolClosedEvent(serverId));
        assertThatCode(() -> listener.connectionCheckedIn(new ConnectionCheckedInEvent(connectionId)))
                .doesNotThrowAnyException();
    }

}
