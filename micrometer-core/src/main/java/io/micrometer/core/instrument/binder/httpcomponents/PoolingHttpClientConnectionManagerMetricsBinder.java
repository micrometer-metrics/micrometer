package io.micrometer.core.instrument.binder.httpcomponents;

import com.google.common.base.Preconditions;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNull;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;

import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Collects metrics from a {@link PoolingHttpClientConnectionManager}.
 *
 * It monitors the overall connection pool and can also be used to monitor
 * connection pools per route.
 *
 * @author Benjamin Hubert (benjamin.hubert@willhaben.at)
 * @since 1.2.0
 */
public class PoolingHttpClientConnectionManagerMetricsBinder implements MeterBinder {

    private static final String NAME_HTTPCLIENT_CONNECTION_POOL_TOTAL_MAX             = "httpcomponents.httpclient.pool.total.max";
    private static final String NAME_HTTPCLIENT_CONNECTION_POOL_TOTAL_AVAILABLE       = "httpcomponents.httpclient.pool.total.available";
    private static final String NAME_HTTPCLIENT_CONNECTION_POOL_TOTAL_LEASED          = "httpcomponents.httpclient.pool.total.leased";
    private static final String NAME_HTTPCLIENT_CONNECTION_POOL_TOTAL_PENDING         = "httpcomponents.httpclient.pool.total.pending";
    private static final String NAME_HTTPCLIENT_CONNECTION_POOL_DEFAULT_MAX_PER_ROUTE = "httpcomponents.httpclient.pool.route.max.default";
    private static final String NAME_HTTPCLIENT_CONNECTION_POOL_ROUTE_MAX             = "httpcomponents.httpclient.pool.route.max";
    private static final String NAME_HTTPCLIENT_CONNECTION_POOL_ROUTE_AVAILABLE       = "httpcomponents.httpclient.pool.route.available";
    private static final String NAME_HTTPCLIENT_CONNECTION_POOL_ROUTE_LEASED          = "httpcomponents.httpclient.pool.route.leased";
    private static final String NAME_HTTPCLIENT_CONNECTION_POOL_ROUTE_PENDING         = "httpcomponents.httpclient.pool.route.pending";

    private final ScheduledExecutorService routeUpdateTask = Executors.newScheduledThreadPool(1);
    private final PoolingHttpClientConnectionManager connectionManager;
    private final Iterable<Tag> tags;
    private final boolean monitorRoutes;

    private MultiGauge poolRouteMaxGauge;
    private MultiGauge poolRouteAvailableGauge;
    private MultiGauge poolRouteLeasedGauge;
    private MultiGauge poolRoutePendingGauge;

    /**
     * Creates a metrics binder for the given pooling connection manager.
     * This instance will not monitor pool statistics of specific routes.
     *
     * @param connectionManager The connection manager to monitor.
     * @param name Name of the connection manager. Will be added as tag with the
     *             key "httpclient".
     * @param tags Tags to apply to all recorded metrics. Must be an even number
     *             of arguments representing key/value pairs of tags.
     */
    public PoolingHttpClientConnectionManagerMetricsBinder(HttpClientConnectionManager connectionManager, String name, String... tags) {
        this(connectionManager, name, Tags.of(tags));
    }

    /**
     * Creates a metrics binder for the given pooling connection manager.
     * This instance will not monitor pool statistics of specific routes.
     *
     * @param connectionManager The connection manager to monitor.
     * @param name Name of the connection manager. Will be added as tag with the
     *             key "httpclient".
     * @param monitorRoutes When true, this metrics binder will monitor every
     *                      route (target scheme, host and port). Be careful:
     *                      Set this to true if and only if you are sure that
     *                      your service accesses only a limited number of
     *                      target schemes, hosts and ports. DO NOT ENABLE THIS
     *                      FEATURE if your HttpClient accesses an unlimited
     *                      number of routes (i.e. if the target host depends on
     *                      the user input).
     * @param tags Tags to apply to all recorded metrics. Must be an even number
     *             of arguments representing key/value pairs of tags.
     */
    public PoolingHttpClientConnectionManagerMetricsBinder(HttpClientConnectionManager connectionManager, String name, boolean monitorRoutes, String... tags) {
        this(connectionManager, name, monitorRoutes, Tags.of(tags));
    }

    /**
     * Creates a metrics binder for the given pooling connection manager.
     * This instance will not monitor pool statistics of specific routes.
     *
     * @param connectionManager The connection manager to monitor.
     * @param name Name of the connection manager. Will be added as tag with the
     *             key "httpclient".
     * @param tags Tags to apply to all recorded metrics.
     */
    public PoolingHttpClientConnectionManagerMetricsBinder(HttpClientConnectionManager connectionManager, String name, Iterable<Tag> tags) {
        this(connectionManager, name, false, tags);
    }

    /**
     * Creates a metrics binder for the given pooling connection manager.
     *
     * @param connectionManager The connection manager to monitor.
     * @param name Name of the connection manager. Will be added as tag with the
     *             key "httpclient".
     * @param monitorRoutes When true, this metrics binder will monitor every
     *                      route (target scheme, host and port). Be careful:
     *                      Set this to true if and only if you are sure that
     *                      your service accesses only a limited number of
     *                      target schemes, hosts and ports. DO NOT ENABLE THIS
     *                      FEATURE if your HttpClient accesses an unlimited
     *                      number of routes (i.e. if the target host depends on
     *                      the user input).
     * @param tags Tags to apply to all recorded metrics.
     */
    public PoolingHttpClientConnectionManagerMetricsBinder(HttpClientConnectionManager connectionManager, String name, boolean monitorRoutes, Iterable<Tag> tags) {
        Preconditions.checkArgument(connectionManager instanceof PoolingHttpClientConnectionManager);
        this.connectionManager = (PoolingHttpClientConnectionManager) connectionManager;
        this.tags = Tags.concat(tags, "httpclient", name);
        this.monitorRoutes = monitorRoutes;
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        Gauge.builder(NAME_HTTPCLIENT_CONNECTION_POOL_TOTAL_MAX,
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getMax())
            .tags(tags)
            .register(registry);
        Gauge.builder(NAME_HTTPCLIENT_CONNECTION_POOL_TOTAL_AVAILABLE,
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getAvailable())
            .tags(tags)
            .register(registry);
        Gauge.builder(NAME_HTTPCLIENT_CONNECTION_POOL_TOTAL_LEASED,
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getLeased())
            .tags(tags)
            .register(registry);
        Gauge.builder(NAME_HTTPCLIENT_CONNECTION_POOL_TOTAL_PENDING,
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getPending())
            .tags(tags)
            .register(registry);
        Gauge.builder(NAME_HTTPCLIENT_CONNECTION_POOL_DEFAULT_MAX_PER_ROUTE,
            connectionManager,
            PoolingHttpClientConnectionManager::getDefaultMaxPerRoute)
            .tags(tags)
            .register(registry);

        if (monitorRoutes) {
            poolRouteMaxGauge = MultiGauge.builder(NAME_HTTPCLIENT_CONNECTION_POOL_ROUTE_MAX)
                .tags(tags)
                .register(registry);
            poolRouteAvailableGauge = MultiGauge.builder(NAME_HTTPCLIENT_CONNECTION_POOL_ROUTE_AVAILABLE)
                .tags(tags)
                .register(registry);
            poolRouteLeasedGauge = MultiGauge.builder(NAME_HTTPCLIENT_CONNECTION_POOL_ROUTE_LEASED)
                .tags(tags)
                .register(registry);
            poolRoutePendingGauge = MultiGauge.builder(NAME_HTTPCLIENT_CONNECTION_POOL_ROUTE_PENDING)
                .tags(tags)
                .register(registry);

            routeUpdateTask.scheduleAtFixedRate(new RouteUpdater(), 10, 10, TimeUnit.SECONDS);
            routeUpdateTask.execute(new RouteUpdater());
        }
    }

    @PreDestroy
    public void shutdown() {
        routeUpdateTask.shutdownNow();
    }

    private class RouteUpdater implements Runnable {
        @Override
        public void run() {
            Set<HttpRoute> routes = connectionManager.getRoutes();
            poolRouteMaxGauge.register(toRows(routes, PoolStats::getMax));
            poolRouteAvailableGauge.register(toRows(routes, PoolStats::getAvailable));
            poolRouteLeasedGauge.register(toRows(routes, PoolStats::getLeased));
            poolRoutePendingGauge.register(toRows(routes, PoolStats::getPending));
        }

        private Set<MultiGauge.Row> toRows(Set<HttpRoute> routes, Function<PoolStats, Integer> valueFunction) {
            return routes.stream()
                .map((route) -> toRow(route, () -> valueFunction.apply(connectionManager.getStats(route))))
                .collect(Collectors.toSet());
        }

        private MultiGauge.Row toRow(HttpRoute route, Supplier<Number> valueFunction) {
            Tags tags = Tags.of(
                "target.host", route.getTargetHost().getHostName(),
                "target.port", String.valueOf(route.getTargetHost().getPort()),
                "target.scheme", route.getTargetHost().getSchemeName()
            );
            return MultiGauge.Row.of(tags, valueFunction);
        }
    }

}
