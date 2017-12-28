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

import com.hazelcast.core.IMap;
import com.hazelcast.monitor.LocalMapStats;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.util.Assert;

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
     * @param tags     Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
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
        Assert.notNull(cache, "cache");
        Assert.notNull(tags, "tags");
        Assert.notNull(name, "name");
        this.cache = cache;
        this.name = name;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(name + ".requests", cache, cache -> cache.getLocalMapStats().getHits())
            .tags(tags).tags("result", "hit")
            .description("The number of times cache lookup methods have returned a cached value")
            .register(registry);

        Gauge.builder(name + ".requests", cache,
            cache -> {
                LocalMapStats stats = cache.getLocalMapStats();
                return stats.getGetOperationCount() - stats.getHits();
            })
            .tags(tags).tags("result", "miss")
            .description("The number of times cache lookup methods have not returned a value")
            .register(registry);

        Gauge.builder(name + ".entries", cache, cache -> cache.getLocalMapStats().getBackupEntryCount())
            .tags(tags).tags("ownership", "backup")
            .description("The number of backup entries held by this member")
            .register(registry);

        Gauge.builder(name + ".entries", cache, cache -> cache.getLocalMapStats().getOwnedEntryCount())
            .tags(tags).tags("ownership", "owned")
            .description("The number of owned entries held by this member")
            .register(registry);

        Gauge.builder(name + ".entry.memory", cache, cache -> cache.getLocalMapStats().getBackupEntryMemoryCost())
            .tags(tags).tags("ownership", "backup")
            .description("Memory cost of backup entries held by this member")
            .baseUnit("bytes")
            .register(registry);

        Gauge.builder(name + ".entry.memory", cache, cache -> cache.getLocalMapStats().getOwnedEntryMemoryCost())
            .tags(tags).tags("ownership", "owned")
            .description("Memory cost of owned entries held by this member")
            .baseUnit("bytes")
            .register(registry);

        timings(registry);
        nearCacheMetrics(registry);
    }

    private void nearCacheMetrics(MeterRegistry registry) {
        if (cache.getLocalMapStats().getNearCacheStats() != null) {
            Gauge.builder(name + ".near.requests", cache, cache -> cache.getLocalMapStats().getNearCacheStats().getHits())
                .tags(tags).tags("result", "hit")
                .description("The number of hits (reads) of near cache entries owned by this member")
                .register(registry);

            Gauge.builder(name + ".near.requests", cache, cache -> cache.getLocalMapStats().getNearCacheStats().getMisses())
                .tags(tags).tags("result", "miss")
                .description("The number of hits (reads) of near cache entries owned by this member")
                .register(registry);

            Gauge.builder(name + ".near.evictions", cache, cache -> cache.getLocalMapStats().getNearCacheStats().getEvictions())
                .tags(tags)
                .description("The number of evictions of near cache entries owned by this member")
                .register(registry);

            Gauge.builder(name + ".near.persistences", cache, cache -> cache.getLocalMapStats().getNearCacheStats().getPersistenceCount())
                .tags(tags)
                .description("The number of Near Cache key persistences (when the pre-load feature is enabled)")
                .register(registry);
        }
    }

    private void timings(MeterRegistry registry) {
        FunctionTimer.builder(name + ".gets",
                cache,
                cache -> cache.getLocalMapStats().getGetOperationCount(),
                cache -> cache.getLocalMapStats().getTotalGetLatency(), TimeUnit.NANOSECONDS
            )
            .tags(tags)
            .description("Cache gets")
            .register(registry);

        FunctionTimer.builder(name + ".puts",
                cache,
                cache -> cache.getLocalMapStats().getPutOperationCount(),
                cache -> cache.getLocalMapStats().getTotalPutLatency(), TimeUnit.NANOSECONDS
            )
            .tags(tags)
            .description("Cache puts")
            .register(registry);

        FunctionTimer.builder(name + ".removals",
                cache,
                cache -> cache.getLocalMapStats().getRemoveOperationCount(),
                cache -> cache.getLocalMapStats().getTotalRemoveLatency(), TimeUnit.NANOSECONDS
            )
            .tags(tags)
            .description("Cache removals")
            .register(registry);
    }
}
