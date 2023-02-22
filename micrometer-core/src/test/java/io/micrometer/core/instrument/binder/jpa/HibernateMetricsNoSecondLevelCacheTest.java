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
package io.micrometer.core.instrument.binder.jpa;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link HibernateMetrics}.
 *
 * @author Erin Schnabel
 * @deprecated This implementation is deprecated in favor of the MeterBinder maintained as
 * part of the Hibernate project as of version 5.4.26. See
 * https://mvnrepository.com/artifact/org.hibernate/hibernate-micrometer/
 */
@Deprecated
class HibernateMetricsNoSecondLevelCacheTest {

    private final MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private final SessionFactory sessionFactory = createMockSessionFactory(true);

    private static SessionFactory createMockSessionFactory(boolean statsEnabled) {
        SessionFactory sf = mock(SessionFactory.class);
        final Answer<?> defaultAnswer = inv -> 42L;
        Statistics stats = mock(Statistics.class, defaultAnswer);
        doReturn(statsEnabled).when(stats).isStatisticsEnabled();
        doReturn(new String[] { "region1", "region2" }).when(stats).getSecondLevelCacheRegionNames();
        doReturn(null).when(stats).getDomainDataRegionStatistics("region1");
        doThrow(new IllegalArgumentException("Mocked: Unknown region")).when(stats)
            .getDomainDataRegionStatistics("region2");
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
        assertThat(registry.get("hibernate.sessions.open").functionCounter().count()).isEqualTo(42.0);

        assertThatThrownBy(() -> registry.get("hibernate.second.level.cache.requests").functionCounters())
            .isInstanceOf(MeterNotFoundException.class);
        assertThatThrownBy(() -> registry.get("hibernate.second.level.cache.puts").functionCounters())
            .isInstanceOf(MeterNotFoundException.class);
    }

}
