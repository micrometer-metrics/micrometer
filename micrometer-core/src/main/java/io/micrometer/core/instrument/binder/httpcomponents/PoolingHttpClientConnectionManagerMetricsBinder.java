/*
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
package io.micrometer.core.instrument.binder.httpcomponents;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNull;
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

    private final PoolingHttpClientConnectionManager connectionManager;
    private final Iterable<Tag> tags;

    /**
     * Creates a metrics binder for the given pooling connection manager.
     *
     * @param connectionManager The connection manager to monitor.
     * @param name Name of the connection manager. Will be added as tag with the
     *             key "httpclient".
     * @param tags Tags to apply to all recorded metrics. Must be an even number
     *             of arguments representing key/value pairs of tags.
     */
    @SuppressWarnings("WeakerAccess")
    public PoolingHttpClientConnectionManagerMetricsBinder(PoolingHttpClientConnectionManager connectionManager, String name, String... tags) {
        this(connectionManager, name, Tags.of(tags));
    }

    /**
     * Creates a metrics binder for the given pooling connection manager.
     *
     * @param connectionManager The connection manager to monitor.
     * @param name Name of the connection manager. Will be added as tag with the
     *             key "httpclient".
     * @param tags Tags to apply to all recorded metrics.
     */
    @SuppressWarnings("WeakerAccess")
    public PoolingHttpClientConnectionManagerMetricsBinder(PoolingHttpClientConnectionManager connectionManager, String name, Iterable<Tag> tags) {
        this.connectionManager = connectionManager;
        this.tags = Tags.concat(tags, "httpclient", name);
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        registerTotalMetrics(registry);
    }

    private void registerTotalMetrics(MeterRegistry registry) {
        Gauge.builder("httpcomponents.httpclient.pool.total.max",
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getMax())
            .description("The configured maximum number of allowed persistent connections for all routes.")
            .tags(tags)
            .register(registry);
        Gauge.builder("httpcomponents.httpclient.pool.total.connections",
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getAvailable())
            .description("The number of persistent and available connections for all routes.")
            .tags(tags).tag("state", "available")
            .register(registry);
        Gauge.builder("httpcomponents.httpclient.pool.total.connections",
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getLeased())
            .description("The number of persistent and leased connections for all routes.")
            .tags(tags).tag("state", "leased")
            .register(registry);
        Gauge.builder("httpcomponents.httpclient.pool.total.pending",
            connectionManager,
            (connectionManager) -> connectionManager.getTotalStats().getPending())
            .description("The number of connection requests being blocked awaiting a free connection for all routes.")
            .tags(tags)
            .register(registry);
        Gauge.builder("httpcomponents.httpclient.pool.route.max.default",
            connectionManager,
            PoolingHttpClientConnectionManager::getDefaultMaxPerRoute)
            .description("The configured default maximum number of allowed persistent connections per route.")
            .tags(tags)
            .register(registry);
    }

}
