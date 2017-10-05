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
package io.micrometer.core.instrument.binder.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.statistics.StatisticsGateway;

/**
 * @author Jon Schneider
 */
public class EhCache2Metrics implements MeterBinder {
    private final String name;
    private final Iterable<Tag> tags;
    private final StatisticsGateway stats;

    /**
     * Record metrics on a JCache cache.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static Ehcache monitor(MeterRegistry registry, Ehcache cache, String name, String... tags) {
        return monitor(registry, cache, name, Tags.zip(tags));
    }

    /**
     * Record metrics on a JCache cache.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static Ehcache monitor(MeterRegistry registry, Ehcache cache, String name, Iterable<Tag> tags) {
        new EhCache2Metrics(cache, name, tags).bindTo(registry);
        return cache;
    }
    
    public EhCache2Metrics(Ehcache cache, String name, Iterable<Tag> tags) {
        this.stats = cache.getStatistics();
        this.name = name;
        this.tags = Tags.concat(tags, "name", cache.getName());
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge(registry.createId(name + ".size", Tags.concat(tags, "where", "local"),
            "The number of entries held locally in this cache"),
            stats, StatisticsGateway::getSize);
        registry.gauge(registry.createId(name + ".size", Tags.concat(tags, "where", "remote"),
            "The number of entries held remotely in this cache"),
            stats, StatisticsGateway::getRemoteSize);

        registry.more().counter(registry.createId(name + ".evictions", tags, "Cache evictions"),
            stats, StatisticsGateway::cacheEvictedCount);
        registry.more().counter(registry.createId(name + ".removals", tags, "Cache removals"),
            stats, StatisticsGateway::cacheRemoveCount);

        registry.more().counter(registry.createId(name + ".puts", Tags.concat(tags, "result", "added"), "Cache puts resulting in a new key/value pair"),
            stats, StatisticsGateway::cachePutAddedCount);
        registry.more().counter(registry.createId(name + ".puts", Tags.concat(tags, "result", "updated"), "Cache puts resulting in an updated value"),
            stats, StatisticsGateway::cachePutAddedCount);

        requestMetrics(registry);
        commitTransactionMetrics(registry);
        rollbackTransactionMetrics(registry);
        recoveryTransactionMetrics(registry);

        registry.gauge(registry.createId(name + ".local.offheap.size", tags, "Local off-heap size", "bytes"),
            stats, StatisticsGateway::getLocalOffHeapSize);
        registry.gauge(registry.createId(name + ".local.heap.size", tags, "Local heap size", "bytes"),
            stats, StatisticsGateway::getLocalHeapSizeInBytes);
        registry.gauge(registry.createId(name + ".local.disk.size", tags, "Local disk size", "bytes"),
            stats, StatisticsGateway::getLocalDiskSizeInBytes);
    }

    private void requestMetrics(MeterRegistry registry) {
        registry.more().counter(registry.createId(name + ".requests", Tags.concat(tags, "result", "miss", "reason", "expired"),
            "The number of times cache lookup methods have not returned a value, due to expiry"),
            stats, StatisticsGateway::cacheMissExpiredCount);

        registry.more().counter(registry.createId(name + ".requests", Tags.concat(tags, "result", "miss", "reason", "notFound"),
            "The number of times cache lookup methods have not returned a value, because the key was not found"),
            stats, StatisticsGateway::cacheMissNotFoundCount);

        registry.more().counter(registry.createId(name + ".requests", Tags.concat(tags, "result", "hit"),
            "The number of times cache lookup methods have returned a cached value."),
            stats, StatisticsGateway::cacheHitCount);
    }

    private void commitTransactionMetrics(MeterRegistry registry) {
        registry.more().counter(registry.createId(name + ".xa.commits", Tags.concat(tags, "result", "readOnly"),
            "Transaction commits that had a read-only result"),
            stats, StatisticsGateway::xaCommitReadOnlyCount);

        registry.more().counter(registry.createId(name + ".xa.commits", Tags.concat(tags, "result", "exception"),
            "Transaction commits that failed"),
            stats, StatisticsGateway::xaCommitExceptionCount);

        registry.more().counter(registry.createId(name + ".xa.commits", Tags.concat(tags, "result", "committed"),
            "Transaction commits that failed"),
            stats, StatisticsGateway::xaCommitCommittedCount);
    }

    private void rollbackTransactionMetrics(MeterRegistry registry) {
        registry.more().counter(registry.createId(name + ".xa.rollbacks", Tags.concat(tags, "result", "exception"),
            "Transaction rollbacks that failed"),
            stats, StatisticsGateway::xaRollbackExceptionCount);

        registry.more().counter(registry.createId(name + ".xa.rollbacks", Tags.concat(tags, "result", "success"),
            "Transaction rollbacks that failed"),
            stats, StatisticsGateway::xaRollbackSuccessCount);
    }

    private void recoveryTransactionMetrics(MeterRegistry registry) {
        registry.more().counter(registry.createId(name + ".xa.recoveries", Tags.concat(tags, "result", "nothing"),
            "Recovery transactions that recovered nothing"),
            stats, StatisticsGateway::xaRecoveryNothingCount);

        registry.more().counter(registry.createId(name + ".xa.recoveries", Tags.concat(tags, "result", "success"),
            "Successful recovery transaction"),
            stats, StatisticsGateway::xaRecoveryRecoveredCount);
    }
}
