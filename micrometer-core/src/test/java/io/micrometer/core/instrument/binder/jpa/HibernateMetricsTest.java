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
package io.micrometer.core.instrument.binder.jpa;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.persistence.EntityManagerFactory;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HibernateMetrics}.
 *
 * @author Marten Deinum
 * @author Johnny Lim
 */
@SuppressWarnings("deprecation")
class HibernateMetricsTest {

    private MeterRegistry registry;

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

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    }

    @Test
    void shouldExposeMetricsWhenStatsEnabled() {
        EntityManagerFactory entityManagerFactory = createEntityManagerFactoryMock(true);
        HibernateMetrics.monitor(registry, entityManagerFactory, "entityManagerFactory");
        assertThat(registry.get("hibernate.sessions.open").functionCounter().count()).isEqualTo(42.0);
        assertThat(registry.get("hibernate.sessions.closed").functionCounter().count()).isEqualTo(42.0);

        assertThat(registry.get("hibernate.transactions").tags("result", "success").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.transactions").tags("result", "failure").functionCounter().count()).isEqualTo(0.0d);

        assertThat(registry.get("hibernate.optimistic.failures").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.flushes").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.connections.obtained").functionCounter().count()).isEqualTo(42.0d);

        assertThat(registry.get("hibernate.statements").tags("status", "prepared").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.statements").tags("status", "closed").functionCounter().count()).isEqualTo(42.0d);

        assertThat(registry.get("hibernate.second.level.cache.requests").tags("result", "hit").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.second.level.cache.requests").tags("result", "miss").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.second.level.cache.puts").functionCounter().count()).isEqualTo(42.0d);

        assertThat(registry.get("hibernate.entities.deletes").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.entities.fetches").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.entities.inserts").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.entities.loads").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.entities.updates").functionCounter().count()).isEqualTo(42.0d);

        assertThat(registry.get("hibernate.collections.deletes").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.collections.fetches").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.collections.loads").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.collections.recreates").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.collections.updates").functionCounter().count()).isEqualTo(42.0d);

        assertThat(registry.get("hibernate.cache.natural.id.requests").tags("result", "hit").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.cache.natural.id.requests").tags("result", "miss").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.cache.natural.id.puts").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.query.natural.id.executions").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.query.natural.id.executions.max").timeGauge().value(TimeUnit.MILLISECONDS)).isEqualTo(42.0d);

        assertThat(registry.get("hibernate.query.executions").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.query.executions.max").timeGauge().value(TimeUnit.MILLISECONDS)).isEqualTo(42.0d);

        assertThat(registry.get("hibernate.cache.update.timestamps.requests").tags("result", "hit").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.cache.update.timestamps.requests").tags("result", "miss").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.cache.update.timestamps.puts").functionCounter().count()).isEqualTo(42.0d);

        assertThat(registry.get("hibernate.cache.query.requests").tags("result", "hit").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.cache.query.requests").tags("result", "miss").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.cache.query.puts").functionCounter().count()).isEqualTo(42.0d);
    }

    @Test
    void shouldNotExposeMetricsWhenStatsNotEnabled() {
        EntityManagerFactory entityManagerFactory = createEntityManagerFactoryMock(false);
        HibernateMetrics.monitor(registry, entityManagerFactory, "entityManagerFactory");
        assertThat(registry.find("hibernate.sessions.open").gauge()).isNull();
    }

}
