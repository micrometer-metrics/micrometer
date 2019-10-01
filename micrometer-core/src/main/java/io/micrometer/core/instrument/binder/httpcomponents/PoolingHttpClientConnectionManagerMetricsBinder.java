/*
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
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * Collects metrics from a {@link PoolingHttpClientConnectionManager}.
 *
 * It monitors the overall connection pool state.
 *
 * @author Benjamin Hubert (benjamin.hubert@willhaben.at)
 * @since 1.3.0
 */
public class PoolingHttpClientConnectionManagerMetricsBinder implements MeterBinder {

    private static final String METER_TOTAL_MAX_DESC = "The configured maximum number of allowed persistent connections for all routes.";
    private static final String METER_TOTAL_MAX = "httpcomponents.httpclient.pool.total.max";
    private static final String METER_TOTAL_CONNECTIONS_DESC = "The number of persistent and leased connections for all routes.";
    private static final String METER_TOTAL_CONNECTIONS = "httpcomponents.httpclient.pool.total.connections";
    private static final String METER_TOTAL_PENDING_DESC = "The number of connection requests being blocked awaiting a free connection for all routes.";
    private static final String METER_TOTAL_PENDING = "httpcomponents.httpclient.pool.total.pending";
    private static final String METER_DEFAULT_MAX_PER_ROUTE_DESC = "The configured default maximum number of allowed persistent connections per route.";
    private static final String METER_DEFAULT_MAX_PER_ROUTE = "httpcomponents.httpclient.pool.route.max.default";
    private static final String TAG_CONNECTIONS_STATE = "state";

    private final PoolingHttpClientConnectionManager connectionManager;
    private final Iterable<Tag> tags;

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
    @SuppressWarnings("WeakerAccess")
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
    @SuppressWarnings("WeakerAccess")
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
    }

    private void registerTotalMetrics(MeterRegistry registry) {
        Gauge.builder(METER_TOTAL_MAX,
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getMax())
            .description(METER_TOTAL_MAX_DESC)
            .tags(tags)
            .register(registry);
        Gauge.builder(METER_TOTAL_CONNECTIONS,
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getAvailable())
            .description(METER_TOTAL_CONNECTIONS_DESC)
            .tags(tags).tag(TAG_CONNECTIONS_STATE, "available")
            .register(registry);
        Gauge.builder(METER_TOTAL_CONNECTIONS,
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getLeased())
            .description(METER_TOTAL_CONNECTIONS_DESC)
            .tags(tags).tag(TAG_CONNECTIONS_STATE, "leased")
            .register(registry);
        Gauge.builder(METER_TOTAL_PENDING,
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getPending())
            .description(METER_TOTAL_PENDING_DESC)
            .tags(tags)
            .register(registry);
        Gauge.builder(METER_DEFAULT_MAX_PER_ROUTE,
            connectionManager,
            PoolingHttpClientConnectionManager::getDefaultMaxPerRoute)
            .description(METER_DEFAULT_MAX_PER_ROUTE_DESC)
            .tags(tags)
            .register(registry);
    }

}
