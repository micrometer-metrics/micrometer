/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jpa;

import javax.persistence.EntityManagerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import static org.assertj.core.api.Assertions.assertThat;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link HibernateMetrics}.
 *
 * @author Erin Schnabel
 */
@SuppressWarnings("deprecation")
class HibernateMetricsNoSecondLevelCacheTest {

    private final MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    private final SessionFactory sessionFactory = createMockSessionFactory(true);

    private static EntityManagerFactory createMockEntityManagerFactory(boolean statsEnabled) {
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        SessionFactory sf = createMockSessionFactory(statsEnabled);
        when(emf.unwrap(SessionFactory.class)).thenReturn(sf);
        return emf;
    }

    private static SessionFactory createMockSessionFactory(boolean statsEnabled) {
        SessionFactory sf = mock(SessionFactory.class);
        final Answer<?> defaultAnswer = inv -> 42L;
        Statistics stats = mock(Statistics.class, defaultAnswer);
        doReturn(statsEnabled).when(stats).isStatisticsEnabled();
        doReturn(new String[]{"region1", "region2"}).when(stats).getSecondLevelCacheRegionNames();
        doReturn(null).when(stats).getSecondLevelCacheStatistics("region1");
        doThrow(new IllegalArgumentException("Mocked: Unknown region")).when(stats).getSecondLevelCacheStatistics("region2");
        when(sf.getStatistics()).thenReturn(stats);
        return sf;
    }

    @Test
    void monitorShouldExposeMetricsWhenStatsEnabled() {
        HibernateMetrics.monitor(registry, sessionFactory, "sessionFactory");
        assertThatMonitorShouldExposeMetricsWhenStatsEnabled();
    }

    private void assertThatMonitorShouldExposeMetricsWhenStatsEnabled() {
        // Global cache statistics should still be emitted
        // It should not throw an java.lang.IllegalArgumentException
        // see https://github.com/micrometer-metrics/micrometer/issues/2334
        assertThat(registry.get("hibernate.second.level.cache.requests").tags("result", "hit", "region", "all").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.second.level.cache.requests").tags("result", "miss", "region", "all").functionCounter().count()).isEqualTo(42.0d);
        assertThat(registry.get("hibernate.second.level.cache.puts").tags("region", "all").functionCounter().count()).isEqualTo(42.0d);
   }

}
