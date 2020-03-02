package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.ConnectionPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OkHttpConnectionPoolMetricsTest {

    private MeterRegistry registry;
    private ConnectionPool connectionPool;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        connectionPool = mock(ConnectionPool.class);
    }

    @Test
    void creationWithNullConnectionPoolThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OkHttpConnectionPoolMetrics(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new OkHttpConnectionPoolMetrics(null, "irrelevant");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new OkHttpConnectionPoolMetrics(null, Tags.empty());
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new OkHttpConnectionPoolMetrics(null, "irrelevant", Tags.empty());
        });
    }

    @Test
    void creationWithNullNameThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OkHttpConnectionPoolMetrics(connectionPool, (String) null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new OkHttpConnectionPoolMetrics(connectionPool, null, Tags.empty());
        });
    }

    @Test
    void creationWithNullTagsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OkHttpConnectionPoolMetrics(connectionPool, (Tags) null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            new OkHttpConnectionPoolMetrics(connectionPool, "irrelevant.name", null);
        });
    }

    @Test
    void instanceUsesDefaultName() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool);
        instance.bindTo(registry);
        registry.get("okhttp.pool.connection.count"); // does not throw MeterNotFoundException
    }

    @Test
    void instanceUsesDefaultNameAndGivenTag() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, Tags.of("foo", "bar"));
        instance.bindTo(registry);
        registry.get("okhttp.pool.connection.count").tags("foo", "bar"); // does not throw MeterNotFoundException
    }

    @Test
    void instanceUsesGivenName() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, "some.meter");
        instance.bindTo(registry);
        registry.get("some.meter.connection.count"); // does not throw MeterNotFoundException
    }

    @Test
    void instanceUsesGivenNameAndTag() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, "another.meter", Tags.of("bar", "baz"));
        instance.bindTo(registry);
        registry.get("another.meter.connection.count").tags("bar", "baz"); // does not throw MeterNotFoundException
    }

    @Test
    void total() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, Tags.of("foo", "bar"));
        instance.bindTo(registry);
        when(connectionPool.connectionCount()).thenReturn(17);
        assertThat(registry.get("okhttp.pool.connection.count")
                .tags(Tags.of("foo", "bar").and("state", "total"))
                .gauge().value()).isEqualTo(17.0);
    }

    @Test
    void idle() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, Tags.of("foo", "bar"));
        instance.bindTo(registry);
        when(connectionPool.idleConnectionCount()).thenReturn(13);
        assertThat(registry.get("okhttp.pool.connection.count")
                .tags(Tags.of("foo", "bar").and("state", "idle"))
                .gauge().value()).isEqualTo(13.0);
    }

    @Test
    void maxIfGiven() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, "huge.pool", Tags.of("foo", "bar"), 1234);
        instance.bindTo(registry);
        assertThat(registry.get("huge.pool.connection.limit")
                .tags(Tags.of("foo", "bar"))
                .gauge().value()).isEqualTo(1234.0);
    }

    @Test
    void maxIfNotGiven() {
        OkHttpConnectionPoolMetrics instance = new OkHttpConnectionPoolMetrics(connectionPool, "huge.pool", Tags.of("foo", "bar"), null);
        instance.bindTo(registry);
        assertThrows(MeterNotFoundException.class, () -> {
            registry.get("huge.pool.connection.limit")
                    .tags(Tags.of("foo", "bar"))
                    .gauge();
        });
    }

}
