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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.ConnectionPool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ben Hubert
 */
class OkHttpConnectionPoolMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private final ConnectionPool connectionPool = mock(ConnectionPool.class);

    @Test
    void creationWithNullConnectionPoolThrowsException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OkHttpConnectionPoolMetrics(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new OkHttpConnectionPoolMetrics(null, Tags.empty()));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OkHttpConnectionPoolMetrics(null, "irrelevant", Tags.empty()));
    }

    @Test
    void creationWithNullNamePrefixThrowsException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OkHttpConnectionPoolMetrics(connectionPool, null, Tags.empty()));
    }

    @Test
    void creationWithNullTagsThrowsException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OkHttpConnectionPoolMetrics(connectionPool, null));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new OkHttpConnectionPoolMetrics(connectionPool, "irrelevant.name", null));
    }

    @Test
    void instanceUsesDefaultNamePrefix() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool);
        instance.bindTo(registry);
        registry.get("okhttp.pool.connection.count"); // does not throw
                                                      // MeterNotFoundException
    }

    @Test
    void instanceUsesDefaultNamePrefixAndGivenTag() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, Tags.of("foo", "bar"));
        instance.bindTo(registry);
        registry.get("okhttp.pool.connection.count").tags("foo", "bar"); // does not throw
                                                                         // MeterNotFoundException
    }

    @Test
    void instanceUsesGivenNamePrefix() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, "some.meter",
                Tags.empty());
        instance.bindTo(registry);
        registry.get("some.meter.connection.count"); // does not throw
                                                     // MeterNotFoundException
    }

    @Test
    void instanceUsesGivenNamePrefixAndTag() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, "another.meter",
                Tags.of("bar", "baz"));
        instance.bindTo(registry);
        registry.get("another.meter.connection.count").tags("bar", "baz"); // does not
                                                                           // throw
                                                                           // MeterNotFoundException
    }

    @Test
    void activeAndIdle() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, Tags.of("foo", "bar"));
        instance.bindTo(registry);
        when(connectionPool.connectionCount()).thenReturn(17);
        when(connectionPool.idleConnectionCount()).thenReturn(10, 9);

        assertThat(registry.get("okhttp.pool.connection.count")
            .tags(Tags.of("foo", "bar", "state", "active"))
            .gauge()
            .value()).isEqualTo(7.0);
        assertThat(registry.get("okhttp.pool.connection.count")
            .tags(Tags.of("foo", "bar", "state", "idle"))
            .gauge()
            .value()).isEqualTo(10.0);

        assertThat(registry.get("okhttp.pool.connection.count")
            .tags(Tags.of("foo", "bar", "state", "active"))
            .gauge()
            .value()).isEqualTo(8.0);
        assertThat(registry.get("okhttp.pool.connection.count")
            .tags(Tags.of("foo", "bar", "state", "idle"))
            .gauge()
            .value()).isEqualTo(9.0);
    }

    @Test
    void maxIfGiven() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, "huge.pool",
                Tags.of("foo", "bar"), 1234);
        instance.bindTo(registry);
        assertThat(registry.get("huge.pool.connection.limit").tags(Tags.of("foo", "bar")).gauge().value())
            .isEqualTo(1234.0);
    }

    @Test
    void maxIfNotGiven() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, "huge.pool",
                Tags.of("foo", "bar"), null);
        instance.bindTo(registry);
        assertThatExceptionOfType(MeterNotFoundException.class)
            .isThrownBy(() -> registry.get("huge.pool.connection.limit").tags(Tags.of("foo", "bar")).gauge());
    }

}
