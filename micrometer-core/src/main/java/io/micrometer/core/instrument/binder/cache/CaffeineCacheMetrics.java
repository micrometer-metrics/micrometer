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

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import java.util.concurrent.TimeUnit;

/**
 * Collect metrics from Caffeine's com.github.benmanes.caffeine.cache.Cache.
 * <p>
 * Note that `recordStats()` is required to gather non-zero statistics:
 * <pre>{@code
 * Cache<String, String> cache = Caffeine.newBuilder().recordStats().build();
 * CaffeineCacheMetrics.monitor(registry, cache, "mycache", "region", "test");
 * }</pre>
 * <p>
 *
 * @author Clint Checketts
 */
@NonNullApi
@NonNullFields
public class CaffeineCacheMetrics implements MeterBinder {
    private final String name;
    private final Iterable<Tag> tags;
    private final Cache<?, ?> cache;

    /**
     * Creates a new {@link CaffeineCacheMetrics} instance.
     *
     * @param cache The cache to be instrumented. You must call {@link Caffeine#recordStats()} prior to building the cache
     *              for metrics to be recorded.
     * @param name  The metric name prefix
     * @param tags  tags to apply to all recorded metrics
     */
    public CaffeineCacheMetrics(Cache<?, ?> cache, String name, Iterable<Tag> tags) {
        this.name = name;
        this.tags = tags;
        this.cache = cache;
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
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
        new CaffeineCacheMetrics(cache, name, tags).bindTo(registry);
        return cache;
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
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

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(name + ".estimated.size", cache, Cache::estimatedSize)
            .tags(tags)
            .description("The approximate number of entries in this cache")
            .register(registry);

        FunctionCounter.builder(name + ".requests", cache, c -> c.stats().missCount())
            .tags(tags).tags("result", "miss")
            .description("the number of times cache lookup methods have returned an uncached (newly loaded) value, or null")
            .register(registry);

        FunctionCounter.builder(name + ".requests", cache, c -> c.stats().hitCount())
            .tags(tags).tags("result", "hit")
            .description("The number of times cache lookup methods have returned a cached value.")
            .register(registry);

        FunctionCounter.builder(name + ".evictions", cache, c -> c.stats().evictionCount())
            .tags(tags)
            .description("cache evictions")
            .register(registry);

        Gauge.builder(name + ".eviction.weight", cache, c -> c.stats().evictionWeight())
            .tags(tags)
            .description("The sum of weights of evicted entries. This total does not include manual invalidations.")
            .register(registry);

        if (cache instanceof LoadingCache) {
            // dividing these gives you a measure of load latency
            TimeGauge.builder(name + ".load.duration", cache, TimeUnit.NANOSECONDS, c -> c.stats().totalLoadTime())
                .tags(tags)
                .description("The time the cache has spent loading new values")
                .register(registry);

            FunctionCounter.builder(name + ".load", cache, c -> c.stats().loadSuccessCount())
                .tags(tags)
                .tags("result", "success")
                .description("The number of times cache lookup methods have successfully loaded a new value")
                .register(registry);

            FunctionCounter.builder(name + ".load", cache, c -> c.stats().loadFailureCount())
                .tags(tags).tags("result", "failure")
                .description("The number of times {@link Cache} lookup methods failed to load a new value, either " +
                    "because no value was found or an exception was thrown while loading")
                .register(registry);
        }
    }
}
