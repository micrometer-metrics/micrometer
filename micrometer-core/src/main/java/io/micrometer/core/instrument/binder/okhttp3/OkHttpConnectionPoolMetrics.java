package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import okhttp3.ConnectionPool;

import java.util.Collections;
import java.util.Optional;

/**
 * MeterBinder for collecting metrics of a given OkHttp {@link ConnectionPool}.
 *
 * Example usage:
 * <pre>
 *     return new ConnectionPool(connectionPoolSize, connectionPoolKeepAliveMs, TimeUnit.MILLISECONDS);
 *     new OkHttpConnectionPoolMetrics(connectionPool, "okhttp.pool", Tags.of());
 * </pre>
 */
public class OkHttpConnectionPoolMetrics implements MeterBinder {

    private static final String DEFAULT_NAME = "okhttp.pool";

    private final ConnectionPool connectionPool;
    private final String name;
    private final Iterable<Tag> tags;
    private final Double maxIdleConnectionCount;

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
     *
     * @param connectionPool The connection pool to monitor. Must not be null.
     * @param name           The desired name for the exposed metrics. Must not be null.
     */
    public OkHttpConnectionPoolMetrics(ConnectionPool connectionPool, String name) {
        this(connectionPool, name, Collections.emptyList(), null);
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
     *                           not exposed by this instance. Therefore this meter allows to pass
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
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(name + ".connection.count", () -> Integer.valueOf(connectionPool.connectionCount()).doubleValue())
                .tags(Tags.of(tags).and("state", "total"))
                .register(registry);
        Gauge.builder(name + ".connection.count", () -> Integer.valueOf(connectionPool.idleConnectionCount()).doubleValue())
                .tags(Tags.of(tags).and("state", "idle"))
                .register(registry);
        if (this.maxIdleConnectionCount != null) {
            Gauge.builder(name + ".connection.limit", () -> this.maxIdleConnectionCount)
                    .tags(Tags.of(tags).and("state", "idle"))
                    .register(registry);
        }
    }

}
