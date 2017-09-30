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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hazelcast.core.IMap;
import com.hazelcast.monitor.LocalMapStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.concurrent.TimeUnit;

public class HazelcastCacheMetrics implements MeterBinder {
    private final IMap<?, ?> cache;
    private final String name;
    private final Iterable<Tag> tags;

    /**
     * Record metrics on a Hazelcast cache.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static <K, V, C extends IMap<K, V>> C monitor(MeterRegistry registry, C cache, String name, String... tags) {
        return monitor(registry, cache, name, Tags.zip(tags));
    }

    /**
     * Record metrics on a Hazelcast cache.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static <K, V, C extends IMap<K, V>> C monitor(MeterRegistry registry, C cache, String name, Iterable<Tag> tags) {
        new HazelcastCacheMetrics(cache, name, tags).bindTo(registry);
        return cache;
    }

    public HazelcastCacheMetrics(IMap<?, ?> cache, String name, Iterable<Tag> tags) {
        this.cache = cache;
        this.name = name;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        LocalMapStats s = cache.getLocalMapStats();

        registry.gauge(registry.createId(name + ".requests", Tags.concat(tags, "result", "hit"),
            "The number of times cache lookup methods have returned a cached value"),
            cache, cache -> cache.getLocalMapStats().getHits());

        registry.gauge(registry.createId(name + ".requests", Tags.concat(tags, "result", "miss"),
            "The number of times cache lookup methods have not returned a value"),
            cache, cache -> {
                LocalMapStats stats = cache.getLocalMapStats();
                return stats.getGetOperationCount() - stats.getHits();
            });

        registry.gauge(registry.createId(name + ".entries", Tags.concat(tags, "ownership", "backup"),
            "The number of backup entries held by this member"),
            cache, cache -> cache.getLocalMapStats().getBackupEntryCount());

        registry.gauge(registry.createId(name + ".entries", Tags.concat(tags, "ownership", "owned"),
            "The number of owned entries held by this member"),
            cache, cache -> cache.getLocalMapStats().getOwnedEntryCount());

        registry.gauge(registry.createId(name + ".entry.memory", Tags.concat(tags, "ownership", "backup"),
            "Memory cost of backup entries held by this member", "bytes"),
            cache, cache -> cache.getLocalMapStats().getBackupEntryMemoryCost());

        registry.gauge(registry.createId(name + ".entry.memory", Tags.concat(tags, "ownership", "owned"),
            "Memory cost of owned entries held by this member", "bytes"),
            cache, cache -> cache.getLocalMapStats().getOwnedEntryMemoryCost());

        timings(registry);
        nearCacheMetrics(registry);
    }

    private void nearCacheMetrics(MeterRegistry registry) {
        if (cache.getLocalMapStats().getNearCacheStats() != null) {
            registry.gauge(registry.createId(name + ".near.requests", Tags.concat(tags, "result", "hit"),
                "The number of hits (reads) of near cache entries owned by this member"),
                cache, cache -> cache.getLocalMapStats().getNearCacheStats().getHits());

            registry.gauge(registry.createId(name + ".near.requests", Tags.concat(tags, "result", "miss"),
                "The number of hits (reads) of near cache entries owned by this member"),
                cache, cache -> cache.getLocalMapStats().getNearCacheStats().getMisses());

            registry.gauge(registry.createId(name + ".near.evictions", tags,
                "The number of evictions of near cache entries owned by this member"),
                cache, cache -> cache.getLocalMapStats().getNearCacheStats().getEvictions());

            registry.gauge(registry.createId(name + ".near.persistences", tags,
                "The number of Near Cache key persistences (when the pre-load feature is enabled)"),
                cache, cache -> cache.getLocalMapStats().getNearCacheStats().getPersistenceCount());
        }
    }

    private void timings(MeterRegistry registry) {
        registry.more().timer(registry.createId(name + ".gets", tags, "Cache gets"),
            cache,
            cache -> cache.getLocalMapStats().getGetOperationCount(),
            cache -> cache.getLocalMapStats().getTotalGetLatency(), TimeUnit.NANOSECONDS);

        registry.more().timer(registry.createId(name + ".puts", tags, "Cache puts"),
            cache,
            cache -> cache.getLocalMapStats().getPutOperationCount(),
            cache -> cache.getLocalMapStats().getTotalPutLatency(), TimeUnit.NANOSECONDS);

        registry.more().timer(registry.createId(name + ".removals", tags, "Cache removals"),
            cache,
            cache -> cache.getLocalMapStats().getRemoveOperationCount(),
            cache -> cache.getLocalMapStats().getTotalRemoveLatency(), TimeUnit.NANOSECONDS);
    }
}
