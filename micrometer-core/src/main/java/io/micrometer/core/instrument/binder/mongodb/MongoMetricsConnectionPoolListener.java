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

import com.mongodb.client.MongoClient;
import com.mongodb.connection.ServerId;
import com.mongodb.event.*;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ConnectionPoolListener} for collecting connection pool metrics from
 * {@link MongoClient}.
 *
 * @author Christophe Bornet
 * @author Jonatan Ivanov
 * @since 1.2.0
 * @implNote This implementation requires MongoDB Java driver 4 or later.
 */
@NonNullApi
@NonNullFields
@Incubating(since = "1.2.0")
public class MongoMetricsConnectionPoolListener implements ConnectionPoolListener {

    private static final String METRIC_PREFIX = "mongodb.driver.pool.";

    private final Map<ServerId, AtomicInteger> poolSizes = new ConcurrentHashMap<>();

    private final Map<ServerId, AtomicInteger> checkedOutCounts = new ConcurrentHashMap<>();

    private final Map<ServerId, Counter> checkOutFailedCounters = new ConcurrentHashMap<>();

    private final Map<ServerId, AtomicInteger> waitQueueSizes = new ConcurrentHashMap<>();

    private final Map<ServerId, List<Meter>> meters = new ConcurrentHashMap<>();

    private final MeterRegistry registry;

    private final MongoConnectionPoolTagsProvider tagsProvider;

    /**
     * Create a new {@code MongoMetricsConnectionPoolListener}.
     * @param registry registry to use
     */
    public MongoMetricsConnectionPoolListener(MeterRegistry registry) {
        this(registry, new DefaultMongoConnectionPoolTagsProvider());
    }

    /**
     * Create a new {@code MongoMetricsConnectionPoolListener}.
     * @param registry registry to use
     * @param tagsProvider tags provider to use
     * @since 1.7.0
     */
    public MongoMetricsConnectionPoolListener(MeterRegistry registry, MongoConnectionPoolTagsProvider tagsProvider) {
        this.registry = registry;
        this.tagsProvider = tagsProvider;
    }

    @Override
    public void connectionPoolCreated(ConnectionPoolCreatedEvent event) {
        List<Meter> connectionMeters = new ArrayList<>();
        connectionMeters.add(registerGauge(event, METRIC_PREFIX + "size",
                "the current size of the connection pool, including idle and and in-use members", poolSizes));
        connectionMeters.add(registerGauge(event, METRIC_PREFIX + "checkedout",
                "the count of connections that are currently in use", checkedOutCounts));
        connectionMeters.add(registerCounter(event, METRIC_PREFIX + "checkoutfailed",
                "the count of failed attempts to retrieve a connection", checkOutFailedCounters));
        connectionMeters.add(registerGauge(event, METRIC_PREFIX + "waitqueuesize",
                "the current size of the wait queue for a connection from the pool", waitQueueSizes));
        meters.put(event.getServerId(), connectionMeters);
    }

    @Override
    public void connectionPoolClosed(ConnectionPoolClosedEvent event) {
        ServerId serverId = event.getServerId();
        for (Meter meter : meters.get(serverId)) {
            registry.remove(meter);
        }
        meters.remove(serverId);
        poolSizes.remove(serverId);
        checkedOutCounts.remove(serverId);
        checkOutFailedCounters.remove(serverId);
        waitQueueSizes.remove(serverId);
    }

    @Override
    public void connectionCheckOutStarted(ConnectionCheckOutStartedEvent event) {
        AtomicInteger waitQueueSize = waitQueueSizes.get(event.getServerId());
        if (waitQueueSize != null) {
            waitQueueSize.incrementAndGet();
        }
    }

    @Override
    public void connectionCheckedOut(ConnectionCheckedOutEvent event) {
        AtomicInteger checkedOutCount = checkedOutCounts.get(event.getConnectionId().getServerId());
        if (checkedOutCount != null) {
            checkedOutCount.incrementAndGet();
        }

        AtomicInteger waitQueueSize = waitQueueSizes.get(event.getConnectionId().getServerId());
        if (waitQueueSize != null) {
            waitQueueSize.decrementAndGet();
        }
    }

    @Override
    public void connectionCheckOutFailed(ConnectionCheckOutFailedEvent event) {
        AtomicInteger waitQueueSize = waitQueueSizes.get(event.getServerId());
        if (waitQueueSize != null) {
            waitQueueSize.decrementAndGet();
        }

        Counter checkOutFailedCounter = checkOutFailedCounters.get(event.getServerId());
        if (checkOutFailedCounter != null) {
            checkOutFailedCounter.increment();
        }
    }

    @Override
    public void connectionCheckedIn(ConnectionCheckedInEvent event) {
        AtomicInteger checkedOutCount = checkedOutCounts.get(event.getConnectionId().getServerId());
        if (checkedOutCount != null) {
            checkedOutCount.decrementAndGet();
        }
    }

    @Override
    public void connectionCreated(ConnectionCreatedEvent event) {
        AtomicInteger poolSize = poolSizes.get(event.getConnectionId().getServerId());
        if (poolSize != null) {
            poolSize.incrementAndGet();
        }
    }

    @Override
    public void connectionClosed(ConnectionClosedEvent event) {
        AtomicInteger poolSize = poolSizes.get(event.getConnectionId().getServerId());
        if (poolSize != null) {
            poolSize.decrementAndGet();
        }
    }

    private Gauge registerGauge(ConnectionPoolCreatedEvent event, String metricName, String description,
            Map<ServerId, AtomicInteger> metrics) {
        AtomicInteger value = new AtomicInteger();
        metrics.put(event.getServerId(), value);
        return Gauge.builder(metricName, value, AtomicInteger::doubleValue)
            .description(description)
            .tags(tagsProvider.connectionPoolTags(event))
            .register(registry);
    }

    private Counter registerCounter(ConnectionPoolCreatedEvent event, String metricName, String description,
            Map<ServerId, Counter> metrics) {
        Counter counter = Counter.builder(metricName)
            .description(description)
            .tags(tagsProvider.connectionPoolTags(event))
            .register(registry);
        metrics.put(event.getServerId(), counter);
        return counter;
    }

}
