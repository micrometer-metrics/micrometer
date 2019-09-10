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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.Statistics;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A {@link MeterBinder} implementation that provides Hibernate query metrics. It exposes the
 * same statistics as would be exposed when calling {@link Statistics#getQueryStatistics(String)}.
 *
 * @author Pawel Stepien
 */
@NonNullApi
@NonNullFields
public class HibernateQueryMetrics implements MeterBinder {

    private static final String SESSION_FACTORY_TAG_NAME = "entityManagerFactory";

    private final Iterable<Tag> tags;

    private final SessionFactory sessionFactory;

    /**
     * Create {@code HibernateQueryMetrics} and bind to the specified meter registry.
     *
     * @param registry           meter registry to use
     * @param sessionFactory     session factory to use
     * @param sessionFactoryName session factory name as a tag value
     * @param tags               additional tags
     */
    public static void monitor(MeterRegistry registry, SessionFactory sessionFactory, String sessionFactoryName, String... tags) {
        monitor(registry, sessionFactory, sessionFactoryName, Tags.of(tags));
    }

    /**
     * Create {@code HibernateQueryMetrics} and bind to the specified meter registry.
     *
     * @param registry           meter registry to use
     * @param sessionFactory     session factory to use
     * @param sessionFactoryName session factory name as a tag value
     * @param tags               additional tags
     */
    public static void monitor(MeterRegistry registry, SessionFactory sessionFactory, String sessionFactoryName, Iterable<Tag> tags) {
        new HibernateQueryMetrics(sessionFactory, sessionFactoryName, tags).bindTo(registry);
    }

    /**
     * Create a {@code HibernateQueryMetrics}.
     *
     * @param sessionFactory     session factory to use
     * @param sessionFactoryName session factory name as a tag value
     * @param tags               additional tags
     */
    public HibernateQueryMetrics(SessionFactory sessionFactory, String sessionFactoryName, Iterable<Tag> tags) {
        this.tags = Tags.concat(tags, SESSION_FACTORY_TAG_NAME, sessionFactoryName);
        this.sessionFactory = sessionFactory;
    }

    @Override
    public void bindTo(MeterRegistry registry) {

        registerListeners(registry);

    }

    /**
     * Register hibernate loadEvent listener - event trigger the metrics registration,
     * only select queries are recorded in QueryStatistics {@link org.hibernate.stat.spi.StatisticsImplementor}
     *
     * @param meterRegistry meterRegistry to use
     */
    private void registerListeners(MeterRegistry meterRegistry) {
        if (sessionFactory instanceof SessionFactoryImplementor) {
            EventListenerRegistry registry = ((SessionFactoryImplementor) sessionFactory).getServiceRegistry().getService(EventListenerRegistry.class);
            MetricsEventHandler metricsEventHandler = new MetricsEventHandler(meterRegistry);
            registry.appendListeners(EventType.POST_LOAD, metricsEventHandler);
        }

    }

    class MetricsEventHandler implements PostLoadEventListener {

        private final MeterRegistry meterRegistry;

        MetricsEventHandler(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        public void onPostLoad(PostLoadEvent event) {
            registerQueryMetric(event.getSession().getFactory().getStatistics());
        }

        void registerQueryMetric(Statistics statistics) {
            for (String query : statistics.getQueries()) {
                QueryStatistics queryStatistics = statistics.getQueryStatistics(query);
                if (Objects.nonNull(queryStatistics)) {
                    String queryName = query.replace(" ", "_").replace(".", "_").toLowerCase();
                    if (Objects.isNull(Search.in(meterRegistry).tags("query", queryName).functionCounter())) {

                        FunctionCounter.builder("hibernate.query.cache.requests", queryStatistics, QueryStatistics::getCacheHitCount)
                                .tags(tags)
                                .tags("result", "hit", "query", queryName)
                                .description("The number of query cache hits")
                                .register(meterRegistry);

                        FunctionCounter.builder("hibernate.query.cache.requests", queryStatistics, QueryStatistics::getCacheMissCount)
                                .tags(tags)
                                .tags("result", "miss", "query", queryName)
                                .description("The number of query cache miss")
                                .register(meterRegistry);

                        FunctionCounter.builder("hibernate.query.cache.puts", queryStatistics, QueryStatistics::getCacheMissCount)
                                .tags(tags)
                                .tags("query", queryName)
                                .description("The number of putting the query into cache")
                                .register(meterRegistry);

                        FunctionTimer.builder("hibernate.query.executions.total", queryStatistics, QueryStatistics::getExecutionCount, QueryStatistics::getExecutionTotalTime, TimeUnit.MILLISECONDS)
                                .tags(tags)
                                .tags("query", queryName)
                                .description("Function tracked total number of query executions during time")
                                .register(meterRegistry);

                        TimeGauge.builder("hibernate.query.executions.avg", queryStatistics, TimeUnit.MILLISECONDS, QueryStatistics::getExecutionAvgTime)
                                .tags(tags)
                                .tags("query", queryName)
                                .description("Query average execution time")
                                .register(meterRegistry);

                        TimeGauge.builder("hibernate.query.executions.max", queryStatistics, TimeUnit.MILLISECONDS, QueryStatistics::getExecutionMaxTime)
                                .tags(tags)
                                .tags("query", queryName)
                                .description("Query maximum execution time")
                                .register(meterRegistry);

                        TimeGauge.builder("hibernate.query.executions.min", queryStatistics, TimeUnit.MILLISECONDS, QueryStatistics::getExecutionMinTime)
                                .tags(tags)
                                .tags("query", queryName)
                                .description("Query minimum execution time")
                                .register(meterRegistry);

                        FunctionCounter.builder("hibernate.query.executions.rows", queryStatistics, QueryStatistics::getExecutionRowCount)
                                .tags(tags)
                                .tags("query", queryName)
                                .description("The number of rows returned by query")
                                .register(meterRegistry);
                    }
                }
            }
        }
    }
}
