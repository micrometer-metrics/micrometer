/**
 * Copyright 2019 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.httpcomponents;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNull;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;

import java.util.Set;
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

    private final PoolingHttpClientConnectionManager connectionManager;
    private final Iterable<Tag> tags;

    private MultiGauge poolRouteMaxGauge;
    private MultiGauge poolRouteAvailableGauge;
    private MultiGauge poolRouteLeasedGauge;
    private MultiGauge poolRoutePendingGauge;

    /**
     * Creates a metrics binder for the given pooling connection manager.
     *
     * For convenience this constructor will take care of casting the given
     * {@link HttpClientConnectionManager} to the required {@link
     * PoolingHttpClientConnectionManager}. An {@link IllegalArgumentException}
     * is thrown, if the given {@code connectionManager} is not an instance of
     * {@link PoolingHttpClientConnectionManager}.
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
     *
     * For convenience this constructor will take care of casting the given
     * {@link HttpClientConnectionManager} to the required {@link
     * PoolingHttpClientConnectionManager}. An {@link IllegalArgumentException}
     * is thrown, if the given {@code connectionManager} is not an instance of
     * {@link PoolingHttpClientConnectionManager}.
     *
     * @param connectionManager The connection manager to monitor.
     * @param name Name of the connection manager. Will be added as tag with the
     *             key "httpclient".
     * @param tags Tags to apply to all recorded metrics.
     */
    public PoolingHttpClientConnectionManagerMetricsBinder(HttpClientConnectionManager connectionManager, String name, Iterable<Tag> tags) {
        if (!(connectionManager instanceof PoolingHttpClientConnectionManager)) {
            throw new IllegalArgumentException("The given connectionManager is not an instance of PoolingHttpClientConnectionManager.");
        }
        this.connectionManager = (PoolingHttpClientConnectionManager) connectionManager;
        this.tags = Tags.concat(tags, "httpclient", name);
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        registerTotalMetrics(registry);
        registerPerRouteMetrics(registry);
    }

    private void registerTotalMetrics(MeterRegistry registry) {
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
    }

    private void registerPerRouteMetrics(MeterRegistry registry) {
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
    }

    /**
     * Updates the list of routes accessed by the monitored http client.
     *
     * Call this method periodically if you want to monitor thread pools for
     * every accessed route (target scheme, host and port). For example:
     *
     * <pre>
     *     &#x40;Bean
     *     public RouteUpdater routeUpdater(List&#60;PoolingHttpClientConnectionManagerMetricsBinder&#62; metricsBinders) {
     *         return new RouteUpdater(metricsBinders);
     *     }
     *
     *     private static class RouteUpdater {
     *         private final HashSet&#60;PoolingHttpClientConnectionManagerMetricsBinder&#62; metricsBinders = new HashSet&#60;&#62;();
     *
     *         private RouteUpdater(Collection&#60;PoolingHttpClientConnectionManagerMetricsBinder&#62; metricsBinders) {
     *             this.metricsBinders.addAll(metricsBinders);
     *         }
     *
     *         &#x40;Scheduled(fixedRate = 10000)
     *         public void updateRoutes() {
     *             metricsBinders.forEach(PoolingHttpClientConnectionManagerMetricsBinder::updateRoutes);
     *         }
     *     }
     * </pre>
     *
     * BE CAREFUL! Call this if and only if you are sure that your http client
     * accesses only a limited number of remote schemes, hosts and ports. DO NOT
     * USE THIS METHOD if your HttpClient accesses a big or unlimited number of
     * routes (i.e. if the target host depends on the user input).
     */
    public void updateRoutes() {
        Set<HttpRoute> routes = connectionManager.getRoutes();
        poolRouteMaxGauge.register(routesToRows(routes, PoolStats::getMax));
        poolRouteAvailableGauge.register(routesToRows(routes, PoolStats::getAvailable));
        poolRouteLeasedGauge.register(routesToRows(routes, PoolStats::getLeased));
        poolRoutePendingGauge.register(routesToRows(routes, PoolStats::getPending));
    }

    private Set<MultiGauge.Row> routesToRows(Set<HttpRoute> routes, Function<PoolStats, Integer> valueFunction) {
        return routes.stream()
            .map((route) -> routeToRow(route, () -> valueFunction.apply(connectionManager.getStats(route))))
            .collect(Collectors.toSet());
    }

    private MultiGauge.Row routeToRow(HttpRoute route, Supplier<Number> valueFunction) {
        Tags tags = Tags.of(
            "target.host", route.getTargetHost().getHostName(),
            "target.port", String.valueOf(route.getTargetHost().getPort()),
            "target.scheme", route.getTargetHost().getSchemeName()
        );
        return MultiGauge.Row.of(tags, valueFunction);
    }

}
