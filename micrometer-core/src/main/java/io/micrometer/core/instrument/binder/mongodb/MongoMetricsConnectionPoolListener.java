/**
 * Copyright 2019 Pivotal Software, Inc.
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
import com.mongodb.connection.ServerId;
import com.mongodb.event.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ConnectionPoolListener} for collecting connection pool metrics from {@link MongoClient}.
 *
 * @author Christophe Bornet
 */
@NonNullApi
@NonNullFields
public class MongoMetricsConnectionPoolListener extends ConnectionPoolListenerAdapter {

    private static final String METRIC_PREFIX = "mongodb.pool.";
    private static final String POOLSIZE_METRIC_NAME = METRIC_PREFIX + "size";
    private static final String CHECKEDOUT_METRIC_NAME = METRIC_PREFIX + "checkedout";
    private static final String WAITQUEUESIZE_METRIC_NAME = METRIC_PREFIX + "waitqueuesize";

    private final Map<ServerId, AtomicInteger> poolSize = new ConcurrentHashMap<>();
    private final Map<ServerId, AtomicInteger> checkedOutCount = new ConcurrentHashMap<>();
    private final Map<ServerId, AtomicInteger> waitQueueSize = new ConcurrentHashMap<>();

    private final MeterRegistry registry;

    public MongoMetricsConnectionPoolListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void connectionPoolOpened(ConnectionPoolOpenedEvent event) {
        registerGauge(event.getServerId(), POOLSIZE_METRIC_NAME, poolSize);
        registerGauge(event.getServerId(), CHECKEDOUT_METRIC_NAME, checkedOutCount);
        registerGauge(event.getServerId(), WAITQUEUESIZE_METRIC_NAME, waitQueueSize);
    }

    @Override
    public void connectionPoolClosed(ConnectionPoolClosedEvent event) {
        ServerId serverId = event.getServerId();
        poolSize.remove(serverId);
        checkedOutCount.remove(serverId);
        waitQueueSize.remove(serverId);
    }

    @Override
    public void connectionCheckedOut(ConnectionCheckedOutEvent event) {
        checkedOutCount.get(event.getConnectionId().getServerId())
                .incrementAndGet();
    }

    @Override
    public void connectionCheckedIn(ConnectionCheckedInEvent event) {
        checkedOutCount.get(event.getConnectionId().getServerId())
                .decrementAndGet();
    }

    @Override
    public void waitQueueEntered(ConnectionPoolWaitQueueEnteredEvent event) {
        waitQueueSize.get(event.getServerId())
                .incrementAndGet();
    }

    @Override
    public void waitQueueExited(ConnectionPoolWaitQueueExitedEvent event) {
        waitQueueSize.get(event.getServerId())
                .decrementAndGet();
    }

    @Override
    public void connectionAdded(ConnectionAddedEvent event) {
        poolSize.get(event.getConnectionId().getServerId())
                .incrementAndGet();
    }

    @Override
    public void connectionRemoved(ConnectionRemovedEvent event) {
        poolSize.get(event.getConnectionId().getServerId())
                .decrementAndGet();
    }

    private void registerGauge(ServerId serverId, String metricName, Map<ServerId, AtomicInteger> metrics) {
        metrics.put(serverId, new AtomicInteger());
        Gauge.builder(metricName, metrics, p -> p.get(serverId).doubleValue())
                .description(String.format("MongoDB connection pool %s gauge", metricName))
                .tag("cluster.id", serverId.getClusterId().getValue())
                .tag("server.address", serverId.getAddress().toString())
                .register(registry);
    }

}
