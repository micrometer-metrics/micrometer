/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

/**
 * Collect metrics from Caffeine's com.github.benmanes.caffeine.cache.Cache.
 * <p>
 * Note that `recordStats()` is required to gather non-zero statistics:
 * <pre>{@code
 * Cache<String, String> cache = Caffeine.newBuilder().recordStats().build();
 * CaffeineCacheMetrics.monitor(registry, cache, "mycache", "region", "test");
 * }</pre>
 * <p>
 * @author Clint Checketts
 */
public class CaffeineCacheMetrics implements MeterBinder {
    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static <C extends Cache> C monitor(MeterRegistry registry, C cache, String name, String... tags) {
        return monitor(registry, cache, name, Tags.zip(tags));
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see CacheStats
     */
    public static <C extends Cache> C monitor(MeterRegistry registry, C cache, String name, Iterable<Tag> tags) {
        new CaffeineCacheMetrics(name, tags, cache).bindTo(registry);
        return cache;
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static <C extends AsyncLoadingCache> C monitor(MeterRegistry registry, C cache, String name, String... tags) {
        return monitor(registry, cache, name, Tags.zip(tags));
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see CacheStats
     */
    public static <C extends AsyncLoadingCache> C monitor(MeterRegistry registry, C cache, String name, Iterable<Tag> tags) {
        monitor(registry, cache.synchronous(), name, tags);
        return cache;
    }

    private final String name;
    private final Iterable<Tag> tags;
    private final Cache<?, ?> cache;

    /**
     * @param name  The metric name prefix
     * @param cache The cache to be instrumented. You must call {@link Caffeine#recordStats()} prior to building the cache
     *              for metrics to be recorded.
     */
    public CaffeineCacheMetrics(String name, Iterable<Tag> tags, Cache<?, ?> cache) {
        this.name = name;
        this.tags = tags;
        this.cache = cache;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge(name + ".estimated.size", tags, cache, Cache::estimatedSize);

        registry.more().counter(name + ".requests", Tags.zip("result", "miss"), cache, c -> c.stats().missCount());
        registry.more().counter(name + ".requests", Tags.zip("result", "hit"), cache, c -> c.stats().hitCount());
        registry.more().counter(name + ".evictions", tags, cache, c -> c.stats().evictionCount());
        registry.gauge(name + ".eviction.weight", tags, cache, c -> c.stats().evictionWeight());

        if (cache instanceof LoadingCache) {
            // dividing these gives you a measure of load latency
            registry.more().counter(name + ".load.duration", tags, cache, c -> c.stats().totalLoadTime());
            registry.more().counter(name + ".load", Tags.concat(tags, "result", "success"), cache, c -> c.stats().loadSuccessCount());
            registry.more().counter(name + ".load", Tags.concat(tags, "result", "failure"), cache, c -> c.stats().loadFailureCount());
        }
    }
}
