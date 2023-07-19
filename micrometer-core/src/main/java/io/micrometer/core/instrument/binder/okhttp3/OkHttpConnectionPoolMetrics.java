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
package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import okhttp3.ConnectionPool;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * MeterBinder for collecting metrics of a given OkHttp {@link ConnectionPool}.
 * <p>
 * Example usage: <pre>
 *     ConnectionPool connectionPool = new ConnectionPool(connectionPoolSize, connectionPoolKeepAliveMs, TimeUnit.MILLISECONDS);
 *     new OkHttpConnectionPoolMetrics(connectionPool).bindTo(registry);
 * </pre>
 *
 * @author Ben Hubert
 * @since 1.6.0
 */
public class OkHttpConnectionPoolMetrics implements MeterBinder {

    private static final String DEFAULT_NAME_PREFIX = "okhttp.pool";

    private static final String TAG_STATE = "state";

    private final ConnectionPool connectionPool;

    private final String namePrefix;

    private final Iterable<Tag> tags;

    private final Double maxIdleConnectionCount;

    private final ThreadLocal<ConnectionPoolConnectionStats> connectionStats = new ThreadLocal<>();

    /**
     * Creates a meter binder for the given connection pool. Metrics will be exposed using
     * {@value #DEFAULT_NAME_PREFIX} as name prefix.
     * @param connectionPool The connection pool to monitor. Must not be null.
     */
    public OkHttpConnectionPoolMetrics(ConnectionPool connectionPool) {
        this(connectionPool, DEFAULT_NAME_PREFIX, Collections.emptyList(), null);
    }

    /**
     * Creates a meter binder for the given connection pool. Metrics will be exposed using
     * {@value #DEFAULT_NAME_PREFIX} as name prefix.
     * @param connectionPool The connection pool to monitor. Must not be null.
     * @param tags A list of tags which will be passed for all meters. Must not be null.
     */
    public OkHttpConnectionPoolMetrics(ConnectionPool connectionPool, Iterable<Tag> tags) {
        this(connectionPool, DEFAULT_NAME_PREFIX, tags, null);
    }

    /**
     * Creates a meter binder for the given connection pool.
     * @param connectionPool The connection pool to monitor. Must not be null.
     * @param namePrefix The desired name prefix for the exposed metrics. Must not be
     * null.
     * @param tags A list of tags which will be passed for all meters. Must not be null.
     */
    public OkHttpConnectionPoolMetrics(ConnectionPool connectionPool, String namePrefix, Iterable<Tag> tags) {
        this(connectionPool, namePrefix, tags, null);
    }

    /**
     * Creates a meter binder for the given connection pool.
     * @param connectionPool The connection pool to monitor. Must not be null.
     * @param namePrefix The desired name prefix for the exposed metrics. Must not be
     * null.
     * @param tags A list of tags which will be passed for all meters. Must not be null.
     * @param maxIdleConnections The maximum number of idle connections this pool will
     * hold. This value is passed to the {@link ConnectionPool} constructor but is not
     * exposed by this instance. Therefore this binder allows to pass it, to be able to
     * monitor it.
     */
    public OkHttpConnectionPoolMetrics(ConnectionPool connectionPool, String namePrefix, Iterable<Tag> tags,
            Integer maxIdleConnections) {
        if (connectionPool == null) {
            throw new IllegalArgumentException("Given ConnectionPool must not be null.");
        }
        if (namePrefix == null) {
            throw new IllegalArgumentException("Given name prefix must not be null.");
        }
        if (tags == null) {
            throw new IllegalArgumentException("Given list of tags must not be null.");
        }

        this.connectionPool = connectionPool;
        this.namePrefix = namePrefix;
        this.tags = tags;
        this.maxIdleConnectionCount = Optional.ofNullable(maxIdleConnections).map(Integer::doubleValue).orElse(null);
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        String connectionCountName = namePrefix + ".connection.count";
        Gauge.builder(connectionCountName, connectionStats, cs -> {
            if (cs.get() == null) {
                cs.set(new ConnectionPoolConnectionStats());
            }
            return cs.get().getActiveCount();
        })
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The state of connections in the OkHttp connection pool")
            .tags(Tags.of(tags).and(TAG_STATE, "active"))
            .register(registry);

        Gauge.builder(connectionCountName, connectionStats, cs -> {
            if (cs.get() == null) {
                cs.set(new ConnectionPoolConnectionStats());
            }
            return cs.get().getIdleConnectionCount();
        })
            .baseUnit(BaseUnits.CONNECTIONS)
            .description("The state of connections in the OkHttp connection pool")
            .tags(Tags.of(tags).and(TAG_STATE, "idle"))
            .register(registry);

        if (this.maxIdleConnectionCount != null) {
            Gauge.builder(namePrefix + ".connection.limit", () -> this.maxIdleConnectionCount)
                .baseUnit(BaseUnits.CONNECTIONS)
                .description("The maximum idle connection count in an OkHttp connection pool.")
                .tags(Tags.concat(tags))
                .register(registry);
        }
    }

    /**
     * Allow us to coordinate between active and idle, making sure they always sum to the
     * total available connections. Since we're calculating active from total-idle, we
     * want to synchronize on idle to make sure the sum is accurate.
     */
    private final class ConnectionPoolConnectionStats {

        private CountDownLatch uses = new CountDownLatch(0);

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

        private void snapshotStatsIfNecessary() {
            if (uses.getCount() == 0) {
                idle = connectionPool.idleConnectionCount();
                total = connectionPool.connectionCount();
                uses = new CountDownLatch(2);
            }
        }

    }

}
