/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.jpa;

import java.util.function.ToDoubleFunction;

import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * A {@link MeterBinder} implementation that provides Hibernate metrics. It exposes the
 * same stats as would be exposed when calling {@code Statistics#logSummary}.
 *
 * @author Marten Deinum
 * @since 2.0.0
 */
public class HibernateMetrics implements MeterBinder {

    public static void monitor(MeterRegistry meterRegistry, EntityManagerFactory emf, String name, String... tags) {
        monitor(meterRegistry, emf, name, Tags.zip(tags));
    }

    public static void monitor(MeterRegistry meterRegistry, EntityManagerFactory emf, String name, Iterable<Tag> tags) {
        new HibernateMetrics(emf, name, tags).bindTo(meterRegistry);
    }

    private final String name;
    private final Iterable<Tag> tags;
    private final EntityManagerFactory emf;


    private HibernateMetrics(EntityManagerFactory emf, String name, Iterable<Tag> tags) {
        this.emf=emf;
        this.name=name;
        this.tags=tags;
    }

    private void addMetric(MeterRegistry registry, Statistics stats, String name, ToDoubleFunction<Statistics> value) {

        Gauge.builder(name, stats, value)
            .tags(tags).tags("entityManagerFactory", this.name)
            .register(registry);
    }


    @Override
    public void bindTo(MeterRegistry registry) {

        if (!hasStatisticsEnabled(this.emf)) {
            return;
        }

        final Statistics stats = getStatistics(this.emf);

        // Session stats
        addMetric(registry, stats,"hibernate.sessions.open", Statistics::getSessionOpenCount);
        addMetric(registry, stats,"hibernate.sessions.close", Statistics::getSessionCloseCount);

        // Transaction stats
        addMetric(registry, stats,"hibernate.transactions", Statistics::getTransactionCount);
        addMetric(registry, stats,"hibernate.transactions.success", Statistics::getSuccessfulTransactionCount);

        addMetric(registry, stats,"hibernate.optimistic_failure_count", Statistics::getOptimisticFailureCount);
        addMetric(registry, stats,"hibernate.flushes", Statistics::getFlushCount);
        addMetric(registry, stats,"hibernate.connections.obtained", Statistics::getConnectCount);

        // Statements
        addMetric(registry, stats,"hibernate.statements.prepared", Statistics::getPrepareStatementCount);
        addMetric(registry, stats,"hibernate.statements.closed", Statistics::getCloseStatementCount);

        // Second Level Caching
        addMetric(registry, stats,"hibernate.cache.second_level.hits", Statistics::getSecondLevelCacheHitCount);
        addMetric(registry, stats,"hibernate.cache.second_level.misses", Statistics::getSecondLevelCacheMissCount);
        addMetric(registry, stats,"hibernate.cache.second_level.puts", Statistics::getSecondLevelCachePutCount);

        // Entity information
        addMetric(registry, stats,"hibernate.entities.deleted", Statistics::getEntityDeleteCount);
        addMetric(registry, stats,"hibernate.entities.fetched", Statistics::getEntityFetchCount);
        addMetric(registry, stats,"hibernate.entities.inserted", Statistics::getEntityInsertCount);
        addMetric(registry, stats,"hibernate.entities.loaded", Statistics::getEntityLoadCount);
        addMetric(registry, stats,"hibernate.entities.updated", Statistics::getOptimisticFailureCount);

        // Collections
        addMetric(registry, stats,"hibernate.collections.removed", Statistics::getCollectionRemoveCount);
        addMetric(registry, stats,"hibernate.collections.fetched", Statistics::getCollectionFetchCount);
        addMetric(registry, stats,"hibernate.collections.loaded", Statistics::getCollectionLoadCount);
        addMetric(registry, stats,"hibernate.collections.recreated", Statistics::getCollectionRecreateCount);
        addMetric(registry, stats,"hibernate.collections.updated", Statistics::getCollectionUpdateCount);

        // Natural Id cache
        addMetric(registry, stats,"hibernate.cache.natural_id.hits", Statistics::getNaturalIdCacheHitCount);
        addMetric(registry, stats,"hibernate.cache.natural_id.misses", Statistics::getNaturalIdCacheMissCount);
        addMetric(registry, stats,"hibernate.cache.natural_id.puts", Statistics::getNaturalIdCachePutCount);
        addMetric(registry, stats,"hibernate.query.natural_id.execution.count", Statistics::getNaturalIdQueryExecutionCount);
        addMetric(registry, stats,"hibernate.query.natural_id.execution.max_time", Statistics::getNaturalIdQueryExecutionMaxTime);

        // Query stats
        addMetric(registry, stats,"hibernate.query.execution.count", Statistics::getQueryExecutionCount);
        addMetric(registry, stats,"hibernate.query.execution.max_time", Statistics::getQueryExecutionMaxTime);

        // Update timestamp cache
        addMetric(registry, stats,"hibernate.cache.update_timestamps.hits", Statistics::getUpdateTimestampsCacheHitCount);
        addMetric(registry, stats,"hibernate.cache.update_timestamps.misses", Statistics::getUpdateTimestampsCacheMissCount);
        addMetric(registry, stats,"hibernate.cache.update_timestamps.puts", Statistics::getUpdateTimestampsCachePutCount);

        // Query Caching
        addMetric(registry, stats,"hibernate.cache.query.hits", Statistics::getQueryCacheHitCount);
        addMetric(registry, stats,"hibernate.cache.query.misses", Statistics::getQueryCacheMissCount);
        addMetric(registry, stats,"hibernate.cache.query.puts", Statistics::getQueryCachePutCount);
    }

    private boolean hasStatisticsEnabled(EntityManagerFactory emf) {
        final Statistics stats = getStatistics(emf);
        return (stats != null && stats.isStatisticsEnabled());
    }

    /**
     * Get the {@code Statistics} object from the underlying {@code SessionFactory}. If it isn't hibernate that is
     * used return {@code null}.
     *
     * @param emf a {@code EntityManagerFactory}
     * @return the {@code Statistics} from the underlying {@code SessionFactory} or {@code null}.
     */
    private Statistics getStatistics(EntityManagerFactory emf) {
        try {
            SessionFactory sf = emf.unwrap(SessionFactory.class);
            return sf.getStatistics();
        }  catch (PersistenceException pe) {
            return null;
        }
    }

}
