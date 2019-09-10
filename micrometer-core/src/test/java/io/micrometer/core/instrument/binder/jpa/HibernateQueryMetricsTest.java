/**
 * Copyright 2019 Pivotal Software, Inc.
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HibernateQueryMetrics}.
 *
 * @author Pawel Stepien
 */
class HibernateQueryMetricsTest {

    private MeterRegistry registry;

    @BeforeEach
    void setup() {
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    }

    private HibernateQueryMetrics.MetricsEventHandler createMetricsEventHandlerMock() {
        HibernateQueryMetrics hibernateQueryMetricsMock = Mockito.mock(HibernateQueryMetrics.class);
        return hibernateQueryMetricsMock.new MetricsEventHandler(registry);
    }

    private Statistics createQueryStatisticsMock(String query) {
        Statistics statistics = Mockito.mock(Statistics.class);
        QueryStatistics queryStatistics = Mockito.mock(QueryStatistics.class, invocation -> 43L);
        when(statistics.getQueries()).thenReturn(new String[]{query});
        when(statistics.getQueryStatistics(query)).thenReturn(queryStatistics);
        return statistics;
    }


    @Test
    void shouldExposeQueryMetrics() {
        String query = "Select generatedAlias0 from Table as generatedAlias0 where generatedAlias0.param0 :val0";
        String expectedNormalizedQuery = "select_generatedalias0_from_table_as_generatedalias0_where_generatedalias0_param0_:val0";

        HibernateQueryMetrics.MetricsEventHandler eventHandler = createMetricsEventHandlerMock();
        Statistics statistics = createQueryStatisticsMock(query);

        eventHandler.registerQueryMetric(statistics);

        assertThat(registry.get("hibernate.query.cache.requests").tags("result", "hit", "query", expectedNormalizedQuery).functionCounter().count()).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.cache.requests").tags("result", "hit", "query", expectedNormalizedQuery).functionCounter().count()).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.cache.puts").tags("query", expectedNormalizedQuery).functionCounter().count()).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.executions.total").tags("query", expectedNormalizedQuery).functionTimer().count()).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.executions.total").tags("query", expectedNormalizedQuery).functionTimer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.executions.avg").tags("query", expectedNormalizedQuery).timeGauge().value(TimeUnit.MILLISECONDS)).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.executions.max").tags("query", expectedNormalizedQuery).timeGauge().value(TimeUnit.MILLISECONDS)).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.executions.min").tags("query", expectedNormalizedQuery).timeGauge().value(TimeUnit.MILLISECONDS)).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.executions.rows").tags("query", expectedNormalizedQuery).functionCounter().count()).isEqualTo(43.0);
    }
}
