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
package io.micrometer.core.instrument.binder.httpcomponents.hc5;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.core5.pool.ConnPoolControl;

/**
 * Collects metrics from a {@link ConnPoolControl}, for example
 * {@link PoolingHttpClientConnectionManager} for synchronous HTTP clients or
 * {@link PoolingAsyncClientConnectionManager} for asynchronous HTTP clients.
 * <p>
 * It monitors the overall connection pool state. Usage example: <pre>{@code
 *      PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
 *      HttpClient httpClient = HttpClientBuilder.create().setConnectionManager(connectionManager).build();
 *      new PoolingHttpClientConnectionManagerMetricsBinder(connectionManager, "my-pool").bindTo(registry);
 * }</pre>
 *
 * @author Benjamin Hubert (benjamin.hubert@willhaben.at)
 * @since 1.11.0
 */
public class PoolingHttpClientConnectionManagerMetricsBinder implements MeterBinder {

    private final ConnPoolControl<HttpRoute> connPoolControl;

    private final Iterable<Tag> tags;

    /**
     * Creates a metrics binder for the given pooling connection pool control.
     * @param connPoolControl The connection pool control to monitor.
     * @param name Name of the connection pool control. Will be added as tag with the key
     * "httpclient".
     * @param tags Tags to apply to all recorded metrics. Must be an even number of
     * arguments representing key/value pairs of tags.
     */
    @SuppressWarnings("WeakerAccess")
    public PoolingHttpClientConnectionManagerMetricsBinder(ConnPoolControl<HttpRoute> connPoolControl, String name,
            String... tags) {
        this(connPoolControl, name, Tags.of(tags));
    }

    /**
     * Creates a metrics binder for the given connection pool control.
     * @param connPoolControl The connection pool control to monitor.
     * @param name Name of the connection pool control. Will be added as tag with the key
     * "httpclient".
     * @param tags Tags to apply to all recorded metrics.
     */
    @SuppressWarnings("WeakerAccess")
    public PoolingHttpClientConnectionManagerMetricsBinder(ConnPoolControl<HttpRoute> connPoolControl, String name,
            Iterable<Tag> tags) {
        this.connPoolControl = connPoolControl;
        this.tags = Tags.concat(tags, "httpclient", name);
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        registerTotalMetrics(registry);
    }

    private void registerTotalMetrics(MeterRegistry registry) {
        Gauge
            .builder("httpcomponents.httpclient.pool.total.max", connPoolControl,
                    (connPoolControl) -> connPoolControl.getTotalStats().getMax())
            .description("The configured maximum number of allowed persistent connections for all routes.")
            .tags(tags)
            .register(registry);
        Gauge
            .builder("httpcomponents.httpclient.pool.total.connections", connPoolControl,
                    (connPoolControl) -> connPoolControl.getTotalStats().getAvailable())
            .description("The number of persistent and available connections for all routes.")
            .tags(tags)
            .tag("state", "available")
            .register(registry);
        Gauge
            .builder("httpcomponents.httpclient.pool.total.connections", connPoolControl,
                    (connPoolControl) -> connPoolControl.getTotalStats().getLeased())
            .description("The number of persistent and leased connections for all routes.")
            .tags(tags)
            .tag("state", "leased")
            .register(registry);
        Gauge
            .builder("httpcomponents.httpclient.pool.total.pending", connPoolControl,
                    (connPoolControl) -> connPoolControl.getTotalStats().getPending())
            .description("The number of connection requests being blocked awaiting a free connection for all routes.")
            .tags(tags)
            .register(registry);
        Gauge
            .builder("httpcomponents.httpclient.pool.route.max.default", connPoolControl,
                    ConnPoolControl::getDefaultMaxPerRoute)
            .description("The configured default maximum number of allowed persistent connections per route.")
            .tags(tags)
            .register(registry);
    }

}
