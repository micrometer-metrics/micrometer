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
package io.micrometer.core.instrument.binder.cache;

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.statistics.StatisticsGateway;

import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Collect metrics on EhCache caches, including detailed metrics on transactions and
 * storage space.
 *
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class EhCache2Metrics extends CacheMeterBinder<Ehcache> {

    private static final String DESCRIPTION_CACHE_PUTS_ADDED = "Cache puts (added or updated)";

    private static final String DESCRIPTION_CACHE_MISSES = "The number of times cache lookup methods have not returned a value, due to expiry or because the key was not found";

    private static final String DESCRIPTION_CACHE_XA_COMMITS = "The number of transaction commits";

    private static final String DESCRIPTION_CACHE_XA_ROLLBACKS = "The number of transaction rollbacks";

    private static final String DESCRIPTION_CACHE_XA_RECOVERIES = "The number of transaction recoveries";

    public EhCache2Metrics(Ehcache cache, Iterable<Tag> tags) {
        super(cache, cache.getName(), tags);
    }

    /**
     * Record metrics on an EhCache cache.
     * @param registry The registry to bind metrics to.
     * @param cache The cache to instrument.
     * @param tags Tags to apply to all recorded metrics. Must be an even number of
     * arguments representing key/value pairs of tags.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     */
    public static Ehcache monitor(MeterRegistry registry, Ehcache cache, String... tags) {
        return monitor(registry, cache, Tags.of(tags));
    }

    /**
     * Record metrics on an EhCache cache.
     * @param registry The registry to bind metrics to.
     * @param cache The cache to instrument.
     * @param tags Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     */
    public static Ehcache monitor(MeterRegistry registry, Ehcache cache, Iterable<Tag> tags) {
        new EhCache2Metrics(cache, tags).bindTo(registry);
        return cache;
    }

    @Override
    protected Long size() {
        return getOrDefault(StatisticsGateway::getSize, null);
    }

    @Override
    protected long hitCount() {
        return getOrDefault(StatisticsGateway::cacheHitCount, 0L);
    }

    @Override
    protected Long missCount() {
        return getOrDefault(StatisticsGateway::cacheMissCount, null);
    }

    @Override
    protected Long evictionCount() {
        return getOrDefault(StatisticsGateway::cacheEvictedCount, null);
    }

    @Override
    protected long putCount() {
        return getOrDefault(StatisticsGateway::cachePutCount, 0L);
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
        StatisticsGateway stats = getStats();
        Gauge.builder("cache.remoteSize", stats, StatisticsGateway::getRemoteSize).tags(getTagsWithCacheName())
                .description("The number of entries held remotely in this cache").register(registry);

        FunctionCounter.builder("cache.removals", stats, StatisticsGateway::cacheRemoveCount)
                .tags(getTagsWithCacheName()).description("Cache removals").register(registry);

        FunctionCounter.builder("cache.puts.added", stats, StatisticsGateway::cachePutAddedCount)
                .tags(getTagsWithCacheName()).tags("result", "added").description(DESCRIPTION_CACHE_PUTS_ADDED)
                .register(registry);

        FunctionCounter.builder("cache.puts.added", stats, StatisticsGateway::cachePutUpdatedCount)
                .tags(getTagsWithCacheName()).tags("result", "updated").description(DESCRIPTION_CACHE_PUTS_ADDED)
                .register(registry);

        FunctionCounter.builder("cache.expired.count", stats, StatisticsGateway::cacheExpiredCount)
                .tags(getTagsWithCacheName()).description("Cache expired count").register(registry);

        Gauge.builder("cache.hitRatio", stats, StatisticsGateway::cacheHitRatio).tags(getTagsWithCacheName())
                .description(
                        "Cache hit ratio is a measurement of how many content requests a cache is able to fill successfully, compared to how many requests it receives")
                .register(registry);

        Gauge.builder("cache.writeQueueLength", stats, StatisticsGateway::getWriterQueueLength)
                .tags(getTagsWithCacheName())
                .description("the number of units waiting in a queue or present in a system").register(registry);

        missMetrics(registry);
        commitTransactionMetrics(registry);
        rollbackTransactionMetrics(registry);
        recoveryTransactionMetrics(registry);
        localOffHeapMetrics(registry);
        localHeapMetrics(registry);
        localDiskMetrics(registry);

    }

    @Nullable
    private StatisticsGateway getStats() {
        Ehcache cache = getCache();
        return cache != null ? cache.getStatistics() : null;
    }

    private void missMetrics(MeterRegistry registry) {
        StatisticsGateway stats = getStats();
        FunctionCounter.builder("cache.misses", stats, StatisticsGateway::cacheMissExpiredCount)
                .tags(getTagsWithCacheName()).tags("reason", "expired").description(DESCRIPTION_CACHE_MISSES)
                .register(registry);

        FunctionCounter.builder("cache.misses", stats, StatisticsGateway::cacheMissNotFoundCount)
                .tags(getTagsWithCacheName()).tags("reason", "notFound").description(DESCRIPTION_CACHE_MISSES)
                .register(registry);
    }

    private void commitTransactionMetrics(MeterRegistry registry) {
        StatisticsGateway stats = getStats();
        FunctionCounter.builder("cache.xa.commits", stats, StatisticsGateway::xaCommitReadOnlyCount)
                .tags(getTagsWithCacheName()).tags("result", "readOnly").description(DESCRIPTION_CACHE_XA_COMMITS)
                .register(registry);

        FunctionCounter.builder("cache.xa.commits", stats, StatisticsGateway::xaCommitExceptionCount)
                .tags(getTagsWithCacheName()).tags("result", "exception").description(DESCRIPTION_CACHE_XA_COMMITS)
                .register(registry);

        FunctionCounter.builder("cache.xa.commits", stats, StatisticsGateway::xaCommitCommittedCount)
                .tags(getTagsWithCacheName()).tags("result", "committed").description(DESCRIPTION_CACHE_XA_COMMITS)
                .register(registry);
    }

    private void rollbackTransactionMetrics(MeterRegistry registry) {
        StatisticsGateway stats = getStats();
        FunctionCounter.builder("cache.xa.rollbacks", stats, StatisticsGateway::xaRollbackExceptionCount)
                .tags(getTagsWithCacheName()).tags("result", "exception").description(DESCRIPTION_CACHE_XA_ROLLBACKS)
                .register(registry);

        FunctionCounter.builder("cache.xa.rollbacks", stats, StatisticsGateway::xaRollbackSuccessCount)
                .tags(getTagsWithCacheName()).tags("result", "success").description(DESCRIPTION_CACHE_XA_ROLLBACKS)
                .register(registry);
    }

    private void recoveryTransactionMetrics(MeterRegistry registry) {
        StatisticsGateway stats = getStats();
        FunctionCounter.builder("cache.xa.recoveries", stats, StatisticsGateway::xaRecoveryNothingCount)
                .tags(getTagsWithCacheName()).tags("result", "nothing").description(DESCRIPTION_CACHE_XA_RECOVERIES)
                .register(registry);

        FunctionCounter.builder("cache.xa.recoveries", stats, StatisticsGateway::xaRecoveryRecoveredCount)
                .tags(getTagsWithCacheName()).tags("result", "success").description(DESCRIPTION_CACHE_XA_RECOVERIES)
                .register(registry);
    }

    private void localOffHeapMetrics(MeterRegistry registry) {
        StatisticsGateway stats = getStats();

        Gauge.builder("cache.local.offheap.size", stats, StatisticsGateway::getLocalOffHeapSizeInBytes)
                .tags(getTagsWithCacheName()).description("Local off-heap size").baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("cache.local.offheap.hitCount", stats, StatisticsGateway::localOffHeapHitCount)
                .tags(getTagsWithCacheName()).description("Local off-heap hit count").register(registry);

        Gauge.builder("cache.local.offheap.missCount", stats, StatisticsGateway::localOffHeapMissCount)
                .tags(getTagsWithCacheName()).description("Local off-heap miss count").register(registry);

        Gauge.builder("cache.local.offheap.putCount", stats, StatisticsGateway::localOffHeapPutCount)
                .tags(getTagsWithCacheName()).description("Local off-heap put count").register(registry);

        Gauge.builder("cache.local.offheap.putAddedCount", stats, StatisticsGateway::localOffHeapPutAddedCount)
                .tags(getTagsWithCacheName()).description("Local off-heap put added count").register(registry);

        Gauge.builder("cache.local.offheap.putUpdatedCount", stats, StatisticsGateway::localOffHeapPutUpdatedCount)
                .tags(getTagsWithCacheName()).description("Local off-heap put updated count").register(registry);

        Gauge.builder("cache.local.offheap.removeCount", stats, StatisticsGateway::localOffHeapRemoveCount)
                .tags(getTagsWithCacheName()).description("Local off-heap remove count").register(registry);
    }

    private void localHeapMetrics(MeterRegistry registry) {
        StatisticsGateway stats = getStats();

        Gauge.builder("cache.local.heap.size", stats, StatisticsGateway::getLocalHeapSizeInBytes)
                .tags(getTagsWithCacheName()).description("Local heap size").baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("cache.local.heap.hitCount", stats, StatisticsGateway::localHeapHitCount)
                .tags(getTagsWithCacheName()).description("Local heap hit count").register(registry);

        Gauge.builder("cache.local.heap.missCount", stats, StatisticsGateway::localHeapMissCount)
                .tags(getTagsWithCacheName()).description("Local heap miss count").register(registry);

        Gauge.builder("cache.local.heap.putCount", stats, StatisticsGateway::localHeapPutCount)
                .tags(getTagsWithCacheName()).description("Local heap put count").register(registry);

        Gauge.builder("cache.local.heap.putAddedCount", stats, StatisticsGateway::localHeapPutAddedCount)
                .tags(getTagsWithCacheName()).description("Local heap put added count").register(registry);

        Gauge.builder("cache.local.heap.putUpdatedCount", stats, StatisticsGateway::localHeapPutUpdatedCount)
                .tags(getTagsWithCacheName()).description("Local heap put updated count").register(registry);

        Gauge.builder("cache.local.heap.removeCount", stats, StatisticsGateway::localHeapRemoveCount)
                .tags(getTagsWithCacheName()).description("Local heap remove count").register(registry);
    }

    private void localDiskMetrics(MeterRegistry registry) {
        StatisticsGateway stats = getStats();

        Gauge.builder("cache.local.disk.size", stats, StatisticsGateway::getLocalDiskSizeInBytes)
                .tags(getTagsWithCacheName()).description("Local disk size").baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("cache.local.disk.hitCount", stats, StatisticsGateway::localDiskHitCount)
                .tags(getTagsWithCacheName()).description("Local disk hit count").register(registry);

        Gauge.builder("cache.local.disk.missCount", stats, StatisticsGateway::localDiskMissCount)
                .tags(getTagsWithCacheName()).description("Local disk miss count").register(registry);

        Gauge.builder("cache.local.disk.putCount", stats, StatisticsGateway::localDiskPutCount)
                .tags(getTagsWithCacheName()).description("Local disk put count").register(registry);

        Gauge.builder("cache.local.disk.putAddedCount", stats, StatisticsGateway::localDiskPutAddedCount)
                .tags(getTagsWithCacheName()).description("Local disk put added count").register(registry);

        Gauge.builder("cache.local.disk.putUpdatedCount", stats, StatisticsGateway::localDiskPutUpdatedCount)
                .tags(getTagsWithCacheName()).description("Local disk put updated count").register(registry);

        Gauge.builder("cache.local.disk.removeCount", stats, StatisticsGateway::localDiskRemoveCount)
                .tags(getTagsWithCacheName()).description("Local disk remove count").register(registry);
    }

    @Nullable
    private Long getOrDefault(Function<StatisticsGateway, Long> function, @Nullable Long defaultValue) {
        StatisticsGateway ref = getStats();
        if (ref != null) {
            return function.apply(ref);
        }

        return defaultValue;
    }

    private long getOrDefault(ToLongFunction<StatisticsGateway> function, long defaultValue) {
        StatisticsGateway ref = getStats();
        if (ref != null) {
            return function.applyAsLong(ref);
        }

        return defaultValue;
    }

}
