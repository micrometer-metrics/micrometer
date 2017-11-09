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
package io.micrometer.core.instrument.binder.jpa;

import static io.micrometer.core.instrument.MockClock.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManagerFactory;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleConfig;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * @author Marten Deinum
 */
class HibernateMetricsTest {

    private MeterRegistry registry;

    @BeforeEach
    void setup() throws SQLException {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    }

    @Test
    void shouldExposeMetricsWhenStatsEnabled() {
        clock(registry).add(SimpleConfig.DEFAULT_STEP);

        HibernateMetrics.monitor(registry, createEntityManagerFactoryMock(true), "entityManagerFactory");
        assertThat(registry.find("hibernate.sessions.open").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.sessions.closed").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.transactions").tags("result", "success").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.transactions").tags("result", "failure").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(0.0d);

        assertThat(registry.find("hibernate.optimistic.failures").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.flushes").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.connections.obtained").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.statements").tags("status", "prepared").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.statements").tags("status", "closed").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.second.level.cache.requests").tags("result", "hit").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.second.level.cache.requests").tags("result", "miss").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.second.level.cache.puts").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.entities.deletes").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.entities.fetches").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.entities.inserts").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.entities.loads").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.entities.updates").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.collections.deletes").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.collections.fetches").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.collections.loads").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.collections.recreates").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.collections.updates").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.cache.natural.id.requests").tags("result", "hit").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.natural.id.requests").tags("result", "miss").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.natural.id.puts").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.query.natural.id.executions").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.query.natural.id.executions.max").timeGauge().map(g -> g.value(TimeUnit.MILLISECONDS))).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.query.executions").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.query.executions.max").timeGauge().map(g -> g.value(TimeUnit.MILLISECONDS))).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.cache.update.timestamps.requests").tags("result", "hit").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.update.timestamps.requests").tags("result", "miss").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.update.timestamps.puts").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);

        assertThat(registry.find("hibernate.cache.query.requests").tags("result", "hit").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.query.requests").tags("result", "miss").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
        assertThat(registry.find("hibernate.cache.query.puts").functionCounter().map(FunctionCounter::count)).isPresent().hasValue(42.0d);
    }

    @Test
    void shouldNotExposeMetricsWhenStatsNotEnabled() {
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