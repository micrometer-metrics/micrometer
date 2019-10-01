/*
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
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertThat(registry.get("httpcomponents.httpclient.pool.total.connections")
            .tags("httpclient", "test", "state", "available")
            .gauge().value()).isEqualTo(17.0);
    }

    @Test
    void totalLeased() {
        PoolStats poolStats = mock(PoolStats.class);
        when(poolStats.getLeased()).thenReturn(23);
        when(connectionManager.getTotalStats()).thenReturn(poolStats);
        assertThat(registry.get("httpcomponents.httpclient.pool.total.connections")
            .tags("httpclient", "test", "state", "leased")
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

}
