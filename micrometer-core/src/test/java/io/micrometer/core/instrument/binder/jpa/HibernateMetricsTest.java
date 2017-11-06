package io.micrometer.core.instrument.binder.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import javax.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class HibernateMetricsTest {

    private MeterRegistry registry;

    @BeforeEach
    void setup() throws SQLException {
        registry = new SimpleMeterRegistry();
    }

    @Test
    void shouldExposeMetricsWhenStatsEnabled() {
        HibernateMetrics.monitor(registry, createEntityManagerFactoryMock(true), "entityManagerFactory");
        assertThat(registry.find("hibernate.sessions.open").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.sessions.close").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.transactions").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.transactions.success").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.optimistic_failure_count").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.flushes").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.connections.obtained").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.statements.prepared").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.statements.closed").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.cache.second_level.hits").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.second_level.misses").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.second_level.puts").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.entities.deleted").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.entities.fetched").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.entities.inserted").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.entities.loaded").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.entities.updated").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.collections.removed").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.collections.fetched").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.collections.loaded").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.collections.recreated").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.collections.updated").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.cache.natural_id.hits").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.natural_id.misses").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.natural_id.puts").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.query.natural_id.execution.count").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.query.natural_id.execution.max_time").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.query.execution.count").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.query.execution.max_time").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.cache.update_timestamps.hits").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.update_timestamps.misses").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.update_timestamps.puts").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.cache.query.hits").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.query.misses").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.query.puts").gauge().map(Gauge::value)).isPresent().hasValue(42.0d);

    }

    @Test
    void shouldNotExposeMetricsWhenStatsEnabled() {
        HibernateMetrics.monitor(registry, createEntityManagerFactoryMock(false), "entityManagerFactory");
        assertThat(registry.find("hibernate.sessions.open").gauge()).isNotPresent();
    }

    private static EntityManagerFactory createEntityManagerFactoryMock(final boolean statsEnabled) {
        EntityManagerFactory emf = Mockito.mock(EntityManagerFactory.class);
        SessionFactory sf = Mockito.mock(SessionFactory.class);
        Statistics stats = Mockito.mock(Statistics.class, invocation -> {
            if (invocation.getMethod().getName().equals("isStatisticsEnabled")) {
                return statsEnabled;
            }
            return 42L;
        });
        when(emf.unwrap(SessionFactory.class)).thenReturn(sf);
        when(sf.getStatistics()).thenReturn(stats);
        return emf;
    }

}