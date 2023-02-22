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

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hibernate.SessionFactory;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HibernateQueryMetrics}.
 *
 * @author Pawel Stepien
 * @deprecated This implementation is deprecated in favor of the MeterBinder maintained as
 * part of the Hibernate project as of version 5.4.26. See
 * https://mvnrepository.com/artifact/org.hibernate/hibernate-micrometer/
 */
@Deprecated
class HibernateQueryMetricsTest {

    private MeterRegistry registry = new SimpleMeterRegistry();

    private HibernateQueryMetrics hibernateQueryMetrics = new HibernateQueryMetrics(mock(SessionFactory.class),
            "HibernateQueryMetricsTest", Tags.empty());

    @Test
    void metricsEventHandlerRegistersMetrics() {
        String query = "Select generatedAlias0 from Table as generatedAlias0 where generatedAlias0.param0 :val0";

        HibernateQueryMetrics.MetricsEventHandler eventHandler = hibernateQueryMetrics.new MetricsEventHandler(
                registry);
        Statistics statistics = createQueryStatisticsMock(query);

        eventHandler.registerQueryMetric(statistics);

        assertThat(registry.get("hibernate.query.cache.requests")
            .tags("result", "hit", "query", query)
            .functionCounter()
            .count()).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.cache.requests")
            .tags("result", "miss", "query", query)
            .functionCounter()
            .count()).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.cache.puts").tags("query", query).functionCounter().count())
            .isEqualTo(43.0d);
        FunctionTimer timer = registry.get("hibernate.query.execution.total").tags("query", query).functionTimer();
        assertThat(timer.count()).isEqualTo(43.0d);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.execution.max")
            .tags("query", query)
            .timeGauge()
            .value(TimeUnit.MILLISECONDS)).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.execution.min")
            .tags("query", query)
            .timeGauge()
            .value(TimeUnit.MILLISECONDS)).isEqualTo(43.0d);
        assertThat(registry.get("hibernate.query.execution.rows").tags("query", query).functionCounter().count())
            .isEqualTo(43.0);
    }

    private Statistics createQueryStatisticsMock(String query) {
        Statistics statistics = mock(Statistics.class);
        QueryStatistics queryStatistics = mock(QueryStatistics.class, invocation -> 43L);
        when(statistics.getQueries()).thenReturn(new String[] { query });
        when(statistics.getQueryStatistics(query)).thenReturn(queryStatistics);
        return statistics;
    }

}
