/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNull;
import okhttp3.ConnectionPool;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * MeterBinder for collecting metrics of a given OkHttp {@link ConnectionPool}.
 * <p>
 * Example usage:
 * <pre>
 *     return new ConnectionPool(connectionPoolSize, connectionPoolKeepAliveMs, TimeUnit.MILLISECONDS);
 *     new OkHttpConnectionPoolMetrics(connectionPool, "okhttp.pool", Tags.of());
 * </pre>
 *
 * @author Ben Hubert
 * @since 1.6.0
 */
public class OkHttpConnectionPoolMetrics implements MeterBinder {

    private static final String DEFAULT_NAME = "okhttp.pool";

    private final ConnectionPool connectionPool;
    private final String name;
    private final Iterable<Tag> tags;
    private final Double maxIdleConnectionCount;
    private ConnectionPoolConnectionStats connectionStats = new ConnectionPoolConnectionStats();

    /**
     * Creates a meter binder for the given connection pool.
     * Metrics will be exposed using {@link #DEFAULT_NAME} as name.
     *
     * @param connectionPool The connection pool to monitor. Must not be null.
     */
    public OkHttpConnectionPoolMetrics(ConnectionPool connectionPool) {
        this(connectionPool, DEFAULT_NAME, Collections.emptyList(), null);
    }

    /**
     * Creates a meter binder for the given connection pool.
     * Metrics will be exposed using {@link #DEFAULT_NAME} as name.
     *
     * @param connectionPool The connection pool to monitor. Must not be null.
     * @param tags           A list of tags which will be passed for all meters. Must not be null.
     */
    public OkHttpConnectionPoolMetrics(ConnectionPool connectionPool, Iterable<Tag> tags) {
        this(connectionPool, DEFAULT_NAME, tags, null);
    }

    /**
     * Creates a meter binder for the given connection pool.
     *
     * @param connectionPool The connection pool to monitor. Must not be null.
     * @param name           The desired name for the exposed metrics. Must not be null.
     * @param tags           A list of tags which will be passed for all meters. Must not be null.
     */
    public OkHttpConnectionPoolMetrics(ConnectionPool connectionPool, String name, Iterable<Tag> tags) {
        this(connectionPool, name, tags, null);
    }

    /**
     * Creates a meter binder for the given connection pool.
     *
     * @param connectionPool     The connection pool to monitor. Must not be null.
     * @param name               The desired name for the exposed metrics. Must not be null.
     * @param tags               A list of tags which will be passed for all meters. Must not be null.
     * @param maxIdleConnections The maximum number of idle connections this pool will hold. This
     *                           value is passed to the {@link ConnectionPool} constructor but is
     *                           not exposed by this instance. Therefore this binder allows to pass
     *                           it, to be able to monitor it.
     */
    public OkHttpConnectionPoolMetrics(ConnectionPool connectionPool, String name, Iterable<Tag> tags, Integer maxIdleConnections) {
        if (connectionPool == null) {
            throw new IllegalArgumentException("Given ConnectionPool must not be null.");
        }
        if (name == null) {
            throw new IllegalArgumentException("Given name must not be null.");
        }
        if (tags == null) {
            throw new IllegalArgumentException("Given list of tags must not be null.");
        }

        this.connectionPool = connectionPool;
        this.name = name;
        this.tags = tags;
        this.maxIdleConnectionCount = Optional.ofNullable(maxIdleConnections)
                .map(Integer::doubleValue)
                .orElse(null);
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        Gauge.builder(name + ".connection.count", connectionStats, ConnectionPoolConnectionStats::getActiveCount)
                .baseUnit("connections")
                .description("The state of connections in the OkHttp connection pool")
                .tags(Tags.of(tags).and("state", "active"))
                .register(registry);

        Gauge.builder(name + ".connection.count", connectionStats, ConnectionPoolConnectionStats::getIdleConnectionCount)
                .baseUnit("connections")
                .description("The state of connections in the OkHttp connection pool")
                .tags(Tags.of(tags).and("state", "idle"))
                .register(registry);

        if (this.maxIdleConnectionCount != null) {
            Gauge.builder(name + ".connection.limit", () -> this.maxIdleConnectionCount)
                    .baseUnit("connections")
                    .description("The maximum idle connection count in an OkHttp connection pool.")
                    .tags(Tags.concat(tags, "state", "idle"))
                    .register(registry);
        }
    }

    /**
     * Allow us to coordinate between active and idle, making sure they always sum to the total available connections.
     * Since we're calculating active from total-idle, we want to synchronize on idle to make sure the sum is accurate.
     */
    private final class ConnectionPoolConnectionStats {
        private volatile CountDownLatch uses = new CountDownLatch(0);
        private int idle;
        private int total;

        public int getActiveCount() {
            snapshotStatsIfNecessary();
            uses.countDown();
            return total - idle;
        }

        public int getIdleConnectionCount() {
            snapshotStatsIfNecessary();
            uses.countDown();
            return idle;
        }

        private synchronized void snapshotStatsIfNecessary() {
            if (uses.getCount() == 0) {
                idle = connectionPool.idleConnectionCount();
                total = connectionPool.connectionCount();
                uses = new CountDownLatch(2);
            }
        }
    }
}
