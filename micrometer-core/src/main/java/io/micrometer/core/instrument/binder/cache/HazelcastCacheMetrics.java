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
import io.micrometer.core.instrument.binder.cache.HazelcastIMapAdapter.LocalMapStats;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Collect metrics on Hazelcast caches, including detailed metrics on storage space, near
 * cache usage, and timings.
 *
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class HazelcastCacheMetrics extends CacheMeterBinder<Object> {

    private static final String DESCRIPTION_CACHE_ENTRIES = "The number of entries held by this member";

    private static final String DESCRIPTION_CACHE_ENTRY_MEMORY = "Memory cost of entries held by this member";

    private static final String DESCRIPTION_CACHE_NEAR_REQUESTS = "The number of requests (hits or misses) of near cache entries owned by this member";

    private final HazelcastIMapAdapter cache;

    /**
     * Record metrics on a Hazelcast cache.
     * @param registry registry to bind metrics to
     * @param cache Hazelcast IMap cache to instrument
     * @param tags Tags to apply to all recorded metrics. Must be an even number of
     * arguments representing key/value pairs of tags.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     */
    public static Object monitor(MeterRegistry registry, Object cache, String... tags) {
        return monitor(registry, cache, Tags.of(tags));
    }

    /**
     * Record metrics on a Hazelcast cache.
     * @param registry registry to bind metrics to
     * @param cache Hazelcast IMap cache to instrument
     * @param tags Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     */
    public static Object monitor(MeterRegistry registry, Object cache, Iterable<Tag> tags) {
        new HazelcastCacheMetrics(cache, tags).bindTo(registry);
        return cache;
    }

    /**
     * Binder for Hazelcast cache metrics.
     * @param cache Hazelcast IMap cache to instrument
     * @param tags Tags to apply to all recorded metrics.
     */
    public HazelcastCacheMetrics(Object cache, Iterable<Tag> tags) {
        super(cache, HazelcastIMapAdapter.nameOf(cache), tags);
        this.cache = new HazelcastIMapAdapter(cache);
    }

    @Override
    protected Long size() {
        LocalMapStats localMapStats = cache.getLocalMapStats();
        if (localMapStats != null) {
            return localMapStats.getOwnedEntryCount();
        }

        return null;
    }

    /**
     * @return The number of hits against cache entries held in this local partition. Not
     * all gets had to result from a get operation against {@link #cache}. If a get
     * operation elsewhere in the cluster caused a lookup against an entry held in this
     * partition, the hit will be recorded against map stats in this partition and not in
     * the map stats of the calling {@code IMap}.
     */
    @Override
    protected long hitCount() {
        LocalMapStats localMapStats = cache.getLocalMapStats();
        if (localMapStats != null) {
            return localMapStats.getHits();
        }

        return 0L;
    }

    /**
     * @return There is no way to calculate miss count in Hazelcast. See issue #586.
     */
    @Override
    protected Long missCount() {
        return null;
    }

    @Nullable
    @Override
    protected Long evictionCount() {
        return null;
    }

    @Override
    protected long putCount() {
        LocalMapStats localMapStats = cache.getLocalMapStats();
        if (localMapStats != null) {
            return localMapStats.getPutOperationCount() + localMapStats.getSetOperationCount();
        }

        return 0L;
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
        Gauge
            .builder("cache.entries", cache,
                    cache -> getDouble(cache.getLocalMapStats(), LocalMapStats::getBackupEntryCount))
            .tags(getTagsWithCacheName())
            .tag("ownership", "backup")
            .description(DESCRIPTION_CACHE_ENTRIES)
            .register(registry);

        Gauge
            .builder("cache.entries", cache,
                    cache -> getDouble(cache.getLocalMapStats(), LocalMapStats::getOwnedEntryCount))
            .tags(getTagsWithCacheName())
            .tag("ownership", "owned")
            .description(DESCRIPTION_CACHE_ENTRIES)
            .register(registry);

        Gauge
            .builder("cache.entry.memory", cache,
                    cache -> getDouble(cache.getLocalMapStats(), LocalMapStats::getBackupEntryMemoryCost))
            .tags(getTagsWithCacheName())
            .tag("ownership", "backup")
            .description(DESCRIPTION_CACHE_ENTRY_MEMORY)
            .baseUnit(BaseUnits.BYTES)
            .register(registry);

        Gauge
            .builder("cache.entry.memory", cache,
                    cache -> getDouble(cache.getLocalMapStats(), LocalMapStats::getOwnedEntryMemoryCost))
            .tags(getTagsWithCacheName())
            .tag("ownership", "owned")
            .description(DESCRIPTION_CACHE_ENTRY_MEMORY)
            .baseUnit(BaseUnits.BYTES)
            .register(registry);

        FunctionCounter
            .builder("cache.partition.gets", cache,
                    cache -> getDouble(cache.getLocalMapStats(), LocalMapStats::getGetOperationCount))
            .tags(getTagsWithCacheName())
            .description("The total number of get operations executed against this partition")
            .register(registry);

        timings(registry);
        nearCacheMetrics(registry);
    }

    private double getDouble(LocalMapStats localMapStats, ToDoubleFunction<LocalMapStats> function) {
        return localMapStats != null ? function.applyAsDouble(localMapStats) : Double.NaN;
    }

    private void nearCacheMetrics(MeterRegistry registry) {
        LocalMapStats localMapStats = cache.getLocalMapStats();
        if (localMapStats != null && localMapStats.getNearCacheStats() != null) {
            Gauge
                .builder("cache.near.requests", cache,
                        cache -> getDouble(cache.getLocalMapStats(), (stats) -> stats.getNearCacheStats().getHits()))
                .tags(getTagsWithCacheName())
                .tag("result", "hit")
                .description(DESCRIPTION_CACHE_NEAR_REQUESTS)
                .register(registry);

            Gauge
                .builder("cache.near.requests", cache,
                        cache -> getDouble(cache.getLocalMapStats(), (stats) -> stats.getNearCacheStats().getMisses()))
                .tags(getTagsWithCacheName())
                .tag("result", "miss")
                .description(DESCRIPTION_CACHE_NEAR_REQUESTS)
                .register(registry);

            Gauge
                .builder("cache.near.evictions", cache,
                        cache -> getDouble(cache.getLocalMapStats(),
                                (stats) -> stats.getNearCacheStats().getEvictions()))
                .tags(getTagsWithCacheName())
                .description("The number of evictions of near cache entries owned by this member")
                .register(registry);

            Gauge
                .builder("cache.near.persistences", cache,
                        cache -> getDouble(cache.getLocalMapStats(),
                                (stats) -> stats.getNearCacheStats().getPersistenceCount()))
                .tags(getTagsWithCacheName())
                .description("The number of near cache key persistences (when the pre-load feature is enabled)")
                .register(registry);
        }
    }

    private void timings(MeterRegistry registry) {
        FunctionTimer.builder("cache.gets.latency", cache,
                cache -> getLong(cache.getLocalMapStats(), LocalMapStats::getGetOperationCount),
                cache -> getDouble(cache.getLocalMapStats(), LocalMapStats::getTotalGetLatency), TimeUnit.MILLISECONDS)
            .tags(getTagsWithCacheName())
            .description("Cache gets")
            .register(registry);

        FunctionTimer.builder("cache.puts.latency", cache,
                cache -> getLong(cache.getLocalMapStats(), LocalMapStats::getPutOperationCount),
                cache -> getDouble(cache.getLocalMapStats(), LocalMapStats::getTotalPutLatency), TimeUnit.MILLISECONDS)
            .tags(getTagsWithCacheName())
            .description("Cache puts")
            .register(registry);

        FunctionTimer
            .builder("cache.removals.latency", cache,
                    cache -> getLong(cache.getLocalMapStats(), LocalMapStats::getRemoveOperationCount),
                    cache -> getDouble(cache.getLocalMapStats(), LocalMapStats::getTotalRemoveLatency),
                    TimeUnit.MILLISECONDS)
            .tags(getTagsWithCacheName())
            .description("Cache removals")
            .register(registry);
    }

    private long getLong(LocalMapStats localMapStats, ToLongFunction<LocalMapStats> function) {
        return localMapStats != null ? function.applyAsLong(localMapStats) : 0L;
    }

}
