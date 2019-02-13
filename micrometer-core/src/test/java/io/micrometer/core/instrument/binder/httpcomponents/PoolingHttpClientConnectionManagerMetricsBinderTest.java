/**
 * Copyright 2017 Pivotal Software, Inc.
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.HttpHost;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PoolingHttpClientConnectionManagerMetricsBinder}.
 *
 * @author Benjamin Hubert (benjamin.hubert@willhaben.at)
 */
class PoolingHttpClientConnectionManagerMetricsBinderTest {

    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    private PoolingHttpClientConnectionManager connectionManager;
    private PoolingHttpClientConnectionManagerMetricsBinder binder;

    @BeforeEach
    void setup() {
        connectionManager = mock(PoolingHttpClientConnectionManager.class);
        binder = new PoolingHttpClientConnectionManagerMetricsBinder(connectionManager, "test");
        binder.bindTo(registry);
    }

    @Test
    void creationWithNonPoolingHttpClientThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            HttpClientConnectionManager connectionManager = mock(HttpClientConnectionManager.class);
            new PoolingHttpClientConnectionManagerMetricsBinder(connectionManager, "test");
        });
    }

    @Test
    void creationWithPoolingHttpClientIsOk() {
        HttpClientConnectionManager connectionManager = mock(PoolingHttpClientConnectionManager.class);
        new PoolingHttpClientConnectionManagerMetricsBinder(connectionManager, "test");
    }

    @Test
    void totalMax() {
        PoolStats poolStats = mock(PoolStats.class);
        when(poolStats.getMax()).thenReturn(13);
        when(connectionManager.getTotalStats()).thenReturn(poolStats);
        assertThat(registry.get("httpcomponents.httpclient.pool.total.max")
            .tags("httpclient", "test")
            .gauge().value()).isEqualTo(13.0);
    }

    @Test
    void totalAvailable() {
        PoolStats poolStats = mock(PoolStats.class);
        when(poolStats.getAvailable()).thenReturn(17);
        when(connectionManager.getTotalStats()).thenReturn(poolStats);
        assertThat(registry.get("httpcomponents.httpclient.pool.total.available")
            .tags("httpclient", "test")
            .gauge().value()).isEqualTo(17.0);
    }

    @Test
    void totalLeased() {
        PoolStats poolStats = mock(PoolStats.class);
        when(poolStats.getLeased()).thenReturn(23);
        when(connectionManager.getTotalStats()).thenReturn(poolStats);
        assertThat(registry.get("httpcomponents.httpclient.pool.total.leased")
            .tags("httpclient", "test")
            .gauge().value()).isEqualTo(23.0);
    }

    @Test
    void totalPending() {
        PoolStats poolStats = mock(PoolStats.class);
        when(poolStats.getPending()).thenReturn(37);
        when(connectionManager.getTotalStats()).thenReturn(poolStats);
        assertThat(registry.get("httpcomponents.httpclient.pool.total.pending")
            .tags("httpclient", "test")
            .gauge().value()).isEqualTo(37.0);
    }

    @Test
    void routeMaxDefault() {
        when(connectionManager.getDefaultMaxPerRoute()).thenReturn(7);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.max.default")
            .tags("httpclient", "test")
            .gauge().value()).isEqualTo(7.0);
    }

    @Test
    void routeStats() {
        HttpRoute route1 = httpRoute("http", "one.example.com", 80);
        PoolStats route1Stats = mock(PoolStats.class);
        when(route1Stats.getMax()).thenReturn(19);
        when(route1Stats.getLeased()).thenReturn(29);
        when(route1Stats.getPending()).thenReturn(31);
        when(route1Stats.getAvailable()).thenReturn(37);
        when(connectionManager.getStats(route1)).thenReturn(route1Stats);

        HttpRoute route2 = httpRoute("https", "two.example.com", 443);
        PoolStats route2Stats = mock(PoolStats.class);
        when(route2Stats.getMax()).thenReturn(41);
        when(route2Stats.getLeased()).thenReturn(43);
        when(route2Stats.getPending()).thenReturn(47);
        when(route2Stats.getAvailable()).thenReturn(53);
        when(connectionManager.getStats(route2)).thenReturn(route2Stats);

        when(connectionManager.getRoutes()).thenReturn(new HashSet<>(Arrays.asList(route1, route2)));
        binder.updateRoutes();

        assertThat(registry.get("httpcomponents.httpclient.pool.route.max")
            .tags("target.scheme", "http", "target.host", "one.example.com", "target.port", "80")
            .gauge().value()).isEqualTo(19);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.leased")
            .tags("target.scheme", "http", "target.host", "one.example.com", "target.port", "80")
            .gauge().value()).isEqualTo(29);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.pending")
            .tags("target.scheme", "http", "target.host", "one.example.com", "target.port", "80")
            .gauge().value()).isEqualTo(31);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.available")
            .tags("target.scheme", "http", "target.host", "one.example.com", "target.port", "80")
            .gauge().value()).isEqualTo(37);

        assertThat(registry.get("httpcomponents.httpclient.pool.route.max")
            .tags("target.scheme", "https", "target.host", "two.example.com", "target.port", "443")
            .gauge().value()).isEqualTo(41);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.leased")
            .tags("target.scheme", "https", "target.host", "two.example.com", "target.port", "443")
            .gauge().value()).isEqualTo(43);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.pending")
            .tags("target.scheme", "https", "target.host", "two.example.com", "target.port", "443")
            .gauge().value()).isEqualTo(47);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.available")
            .tags("target.scheme", "https", "target.host", "two.example.com", "target.port", "443")
            .gauge().value()).isEqualTo(53);
    }

    @Test
    void noRouteMetricsWhenRoutesNotUpdated() {
        HttpRoute route = httpRoute("http", "one.example.com", 80);
        PoolStats routeStats = mock(PoolStats.class);
        when(routeStats.getMax()).thenReturn(19);
        when(routeStats.getLeased()).thenReturn(29);
        when(routeStats.getPending()).thenReturn(31);
        when(routeStats.getAvailable()).thenReturn(37);
        when(connectionManager.getStats(route)).thenReturn(routeStats);

        when(connectionManager.getRoutes()).thenReturn(new HashSet<>(Collections.singletonList(route)));
        // do not call binder.updateRoutes();

        assertThrows(MeterNotFoundException.class, () -> registry.get("httpcomponents.httpclient.pool.route.max").gauge());
        assertThrows(MeterNotFoundException.class, () -> registry.get("httpcomponents.httpclient.pool.route.leased").gauge());
        assertThrows(MeterNotFoundException.class, () -> registry.get("httpcomponents.httpclient.pool.route.pending").gauge());
        assertThrows(MeterNotFoundException.class, () -> registry.get("httpcomponents.httpclient.pool.route.available").gauge());
    }

    @Test
    void routeMetricsGetUpdated() {
        HttpRoute route = httpRoute("http", "one.example.com", 80);
        PoolStats routeStats = mock(PoolStats.class);
        when(connectionManager.getStats(route)).thenReturn(routeStats);
        when(connectionManager.getRoutes()).thenReturn(new HashSet<>(Collections.singletonList(route)));

        when(routeStats.getLeased()).thenReturn(17);
        binder.updateRoutes();
        assertThat(registry.get("httpcomponents.httpclient.pool.route.leased")
            .tags("target.scheme", "http", "target.host", "one.example.com", "target.port", "80")
            .gauge().value()).isEqualTo(17);

        when(routeStats.getLeased()).thenReturn(19);
        binder.updateRoutes();
        assertThat(registry.get("httpcomponents.httpclient.pool.route.leased")
            .tags("target.scheme", "http", "target.host", "one.example.com", "target.port", "80")
            .gauge().value()).isEqualTo(19);
    }

    private HttpRoute httpRoute(String scheme, String host, Integer port) {
        HttpHost httpHost = new HttpHost(host, port, scheme);
        return new HttpRoute(httpHost);
    }

}
