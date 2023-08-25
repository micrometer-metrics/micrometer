/*
 * Copyright 2017 VMware, Inc.
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

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * A {@link MeterBinder} implementation that provides Hibernate metrics. It exposes the
 * same statistics as would be exposed when calling {@link Statistics#logSummary()}.
 *
 * @author Marten Deinum
 * @author Jon Schneider
 * @author Johnny Lim
 * @implNote This implementation requires Hibernate 5.3 or later.
 * @deprecated This implementation is deprecated in favor of the MeterBinder maintained as
 * part of the Hibernate project as of version 5.4.26. See
 * https://mvnrepository.com/artifact/org.hibernate/hibernate-micrometer/
 */
@NonNullApi
@NonNullFields
@Deprecated
public class HibernateMetrics implements MeterBinder {

    private static final String SESSION_FACTORY_TAG_NAME = "entityManagerFactory";

    private final Iterable<Tag> tags;

    @Nullable
    private final Statistics statistics;

    /**
     * Create {@code HibernateMetrics} and bind to the specified meter registry.
     * @param registry meter registry to use
     * @param sessionFactory session factory to use
     * @param sessionFactoryName session factory name as a tag value
     * @param tags additional tags
     */
    public static void monitor(MeterRegistry registry, SessionFactory sessionFactory, String sessionFactoryName,
            String... tags) {
        monitor(registry, sessionFactory, sessionFactoryName, Tags.of(tags));
    }

    /**
     * Create {@code HibernateMetrics} and bind to the specified meter registry.
     * @param registry meter registry to use
     * @param sessionFactory session factory to use
     * @param sessionFactoryName session factory name as a tag value
     * @param tags additional tags
     */
    public static void monitor(MeterRegistry registry, SessionFactory sessionFactory, String sessionFactoryName,
            Iterable<Tag> tags) {
        new HibernateMetrics(sessionFactory, sessionFactoryName, tags).bindTo(registry);
    }

    /**
     * Create {@code HibernateMetrics} and bind to the specified meter registry.
     * @param registry meter registry to use
     * @param entityManagerFactory entity manager factory to use
     * @param entityManagerFactoryName entity manager factory name as a tag value
     * @param tags additional tags
     * @deprecated since 1.1.2 in favor of
     * {@link #monitor(MeterRegistry, SessionFactory, String, String...)}
     */
    @Deprecated
    public static void monitor(MeterRegistry registry, EntityManagerFactory entityManagerFactory,
            String entityManagerFactoryName, String... tags) {
        monitor(registry, entityManagerFactory, entityManagerFactoryName, Tags.of(tags));
    }

    /**
     * Create {@code HibernateMetrics} and bind to the specified meter registry.
     * @param registry meter registry to use
     * @param entityManagerFactory entity manager factory to use
     * @param entityManagerFactoryName entity manager factory name as a tag value
     * @param tags additional tags
     * @deprecated since 1.1.2 in favor of
     * {@link #monitor(MeterRegistry, SessionFactory, String, Iterable)}
     */
    @Deprecated
    public static void monitor(MeterRegistry registry, EntityManagerFactory entityManagerFactory,
            String entityManagerFactoryName, Iterable<Tag> tags) {
        new HibernateMetrics(entityManagerFactory, entityManagerFactoryName, tags).bindTo(registry);
    }

    /**
     * Create a {@code HibernateMetrics}.
     * @param sessionFactory session factory to use
     * @param sessionFactoryName session factory name as a tag value
     * @param tags additional tags
     */
    public HibernateMetrics(SessionFactory sessionFactory, String sessionFactoryName, Iterable<Tag> tags) {
        this.tags = Tags.concat(tags, SESSION_FACTORY_TAG_NAME, sessionFactoryName);
        Statistics statistics = sessionFactory.getStatistics();
        this.statistics = statistics.isStatisticsEnabled() ? statistics : null;
    }

    /**
     * Create a {@code HibernateMetrics}.
     * @param entityManagerFactory entity manager factory to use
     * @param entityManagerFactoryName entity manager factory name as a tag value
     * @param tags additional tags
     * @deprecated since 1.1.2 in favor of
     * {@link #HibernateMetrics(SessionFactory, String, Iterable)}
     */
    @Deprecated
    public HibernateMetrics(EntityManagerFactory entityManagerFactory, String entityManagerFactoryName,
            Iterable<Tag> tags) {
        this.tags = Tags.concat(tags, SESSION_FACTORY_TAG_NAME, entityManagerFactoryName);
        SessionFactory sessionFactory = unwrap(entityManagerFactory);
        if (sessionFactory != null) {
            Statistics statistics = sessionFactory.getStatistics();
            this.statistics = statistics.isStatisticsEnabled() ? statistics : null;
        }
        else {
            this.statistics = null;
        }
    }

    private void counter(MeterRegistry registry, String name, String description, ToDoubleFunction<Statistics> f,
            String... extraTags) {
        if (this.statistics == null) {
            return;
        }

        FunctionCounter.builder(name, statistics, f)
            .tags(tags)
            .tags(extraTags)
            .description(description)
            .register(registry);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (this.statistics == null) {
            return;
        }

        // Session statistics
        counter(registry, "hibernate.sessions.open", "Sessions opened", Statistics::getSessionOpenCount);
        counter(registry, "hibernate.sessions.closed", "Sessions closed", Statistics::getSessionCloseCount);

        // Transaction statistics
        counter(registry, "hibernate.transactions", "The number of transactions we know to have been successful",
                Statistics::getSuccessfulTransactionCount, "result", "success");
        counter(registry, "hibernate.transactions", "The number of transactions we know to have failed",
                s -> s.getTransactionCount() - s.getSuccessfulTransactionCount(), "result", "failure");
        counter(registry, "hibernate.optimistic.failures",
                "The number of StaleObjectStateExceptions that have occurred", Statistics::getOptimisticFailureCount);

        counter(registry, "hibernate.flushes",
                "The global number of flushes executed by sessions (either implicit or explicit)",
                Statistics::getFlushCount);
        counter(registry, "hibernate.connections.obtained",
                "Get the global number of connections asked by the sessions "
                        + "(the actual number of connections used may be much smaller depending "
                        + "whether you use a connection pool or not)",
                Statistics::getConnectCount);

        // Statements
        counter(registry, "hibernate.statements", "The number of prepared statements that were acquired",
                Statistics::getPrepareStatementCount, "status", "prepared");
        counter(registry, "hibernate.statements", "The number of prepared statements that were released",
                Statistics::getCloseStatementCount, "status", "closed");

        // Second Level Caching

        Arrays.stream(statistics.getSecondLevelCacheRegionNames())
            .filter(this::hasDomainDataRegionStatistics)
            .forEach(regionName -> {
                counter(registry, "hibernate.second.level.cache.requests",
                        "The number of cacheable entities/collections successfully retrieved from the cache",
                        stats -> stats.getDomainDataRegionStatistics(regionName).getHitCount(), "region", regionName,
                        "result", "hit");
                counter(registry, "hibernate.second.level.cache.requests",
                        "The number of cacheable entities/collections not found in the cache and loaded from the database",
                        stats -> stats.getDomainDataRegionStatistics(regionName).getMissCount(), "region", regionName,
                        "result", "miss");
                counter(registry, "hibernate.second.level.cache.puts",
                        "The number of cacheable entities/collections put in the cache",
                        stats -> stats.getDomainDataRegionStatistics(regionName).getPutCount(), "region", regionName);
            });

        // Entity information
        counter(registry, "hibernate.entities.deletes", "The number of entity deletes",
                Statistics::getEntityDeleteCount);
        counter(registry, "hibernate.entities.fetches", "The number of entity fetches",
                Statistics::getEntityFetchCount);
        counter(registry, "hibernate.entities.inserts", "The number of entity inserts",
                Statistics::getEntityInsertCount);
        counter(registry, "hibernate.entities.loads", "The number of entity loads", Statistics::getEntityLoadCount);
        counter(registry, "hibernate.entities.updates", "The number of entity updates",
                Statistics::getEntityUpdateCount);

        // Collections
        counter(registry, "hibernate.collections.deletes", "The number of collection deletes",
                Statistics::getCollectionRemoveCount);
        counter(registry, "hibernate.collections.fetches", "The number of collection fetches",
                Statistics::getCollectionFetchCount);
        counter(registry, "hibernate.collections.loads", "The number of collection loads",
                Statistics::getCollectionLoadCount);
        counter(registry, "hibernate.collections.recreates", "The number of collections recreated",
                Statistics::getCollectionRecreateCount);
        counter(registry, "hibernate.collections.updates", "The number of collection updates",
                Statistics::getCollectionUpdateCount);

        // Natural Id cache
        counter(registry, "hibernate.cache.natural.id.requests",
                "The number of cached naturalId lookups successfully retrieved from cache",
                Statistics::getNaturalIdCacheHitCount, "result", "hit");
        counter(registry, "hibernate.cache.natural.id.requests",
                "The number of cached naturalId lookups not found in cache", Statistics::getNaturalIdCacheMissCount,
                "result", "miss");
        counter(registry, "hibernate.cache.natural.id.puts", "The number of cacheable naturalId lookups put in cache",
                Statistics::getNaturalIdCachePutCount);

        counter(registry, "hibernate.query.natural.id.executions",
                "The number of naturalId queries executed against the database",
                Statistics::getNaturalIdQueryExecutionCount);

        TimeGauge
            .builder("hibernate.query.natural.id.executions.max", statistics, TimeUnit.MILLISECONDS,
                    Statistics::getNaturalIdQueryExecutionMaxTime)
            .description("The maximum query time for naturalId queries executed against the database")
            .tags(tags)
            .register(registry);

        // Query statistics
        counter(registry, "hibernate.query.executions", "The number of executed queries",
                Statistics::getQueryExecutionCount);

        TimeGauge
            .builder("hibernate.query.executions.max", statistics, TimeUnit.MILLISECONDS,
                    Statistics::getQueryExecutionMaxTime)
            .description("The time of the slowest query")
            .tags(tags)
            .register(registry);

        // Update timestamp cache
        counter(registry, "hibernate.cache.update.timestamps.requests",
                "The number of timestamps successfully retrieved from cache",
                Statistics::getUpdateTimestampsCacheHitCount, "result", "hit");
        counter(registry, "hibernate.cache.update.timestamps.requests",
                "The number of tables for which no update timestamps was not found in cache",
                Statistics::getUpdateTimestampsCacheMissCount, "result", "miss");
        counter(registry, "hibernate.cache.update.timestamps.puts", "The number of timestamps put in cache",
                Statistics::getUpdateTimestampsCachePutCount);

        // Query Caching
        counter(registry, "hibernate.cache.query.requests",
                "The number of cached queries successfully retrieved from cache", Statistics::getQueryCacheHitCount,
                "result", "hit");
        counter(registry, "hibernate.cache.query.requests", "The number of cached queries not found in cache",
                Statistics::getQueryCacheMissCount, "result", "miss");
        counter(registry, "hibernate.cache.query.puts", "The number of cacheable queries put in cache",
                Statistics::getQueryCachePutCount);
        counter(registry, "hibernate.cache.query.plan",
                "The global number of query plans successfully retrieved from cache",
                Statistics::getQueryPlanCacheHitCount, "result", "hit");
        counter(registry, "hibernate.cache.query.plan", "The global number of query plans lookups not found in cache",
                Statistics::getQueryPlanCacheMissCount, "result", "miss");
    }

    private boolean hasDomainDataRegionStatistics(String regionName) {
        // In 5.3, getDomainDataRegionStatistics (a new method) will throw an
        // IllegalArgumentException
        // if the region can't be resolved.
        try {
            return statistics.getDomainDataRegionStatistics(regionName) != null;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Unwrap the {@link SessionFactory} from {@link EntityManagerFactory}.
     * @param entityManagerFactory {@link EntityManagerFactory} to unwrap
     * @return unwrapped {@link SessionFactory}
     */
    @Nullable
    private SessionFactory unwrap(EntityManagerFactory entityManagerFactory) {
        try {
            return entityManagerFactory.unwrap(SessionFactory.class);
        }
        catch (PersistenceException ex) {
            return null;
        }
    }

}
