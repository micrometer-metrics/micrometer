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

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.*;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Collect metrics from Caffeine's {@link com.github.benmanes.caffeine.cache.Cache}.
 * {@link CaffeineStatsCounter} is an alternative that can collect more detailed
 * statistics.
 * <p>
 * Note that {@code recordStats()} is required to gather non-zero statistics: <pre>{@code
 * Cache<String, String> cache = Caffeine.newBuilder().recordStats().build();
 * CaffeineCacheMetrics.monitor(registry, cache, "mycache", "region", "test");
 * }</pre>
 *
 * @author Clint Checketts
 * @see CaffeineStatsCounter
 */
@NonNullApi
@NonNullFields
public class CaffeineCacheMetrics<K, V, C extends Cache<K, V>> extends CacheMeterBinder<C> {

    private static final String DESCRIPTION_CACHE_LOAD = "The number of times cache lookup methods have successfully loaded a new value or failed to load a new value, either because no value was found or an exception was thrown while loading";

    private static final InternalLogger log = InternalLoggerFactory.getInstance(CaffeineCacheMetrics.class);

    /**
     * Creates a new {@link CaffeineCacheMetrics} instance.
     * @param cache The cache to be instrumented. You must call
     * {@link Caffeine#recordStats()} prior to building the cache for metrics to be
     * recorded.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags tags to apply to all recorded metrics.
     */
    public CaffeineCacheMetrics(C cache, String cacheName, Iterable<Tag> tags) {
        super(cache, cacheName, tags);

        if (!cache.policy().isRecordingStats()) {
            log.warn(
                    "The cache '{}' is not recording statistics. No meters except 'cache.size' will be registered. Call 'Caffeine#recordStats()' prior to building the cache for metrics to be recorded.",
                    cacheName);
        }
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()}
     * prior to building the cache for metrics to be recorded.
     * @param registry The registry to bind metrics to.
     * @param cache The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags Tags to apply to all recorded metrics. Must be an even number of
     * arguments representing key/value pairs of tags.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @param <C> The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     */
    public static <K, V, C extends Cache<K, V>> C monitor(MeterRegistry registry, C cache, String cacheName,
            String... tags) {
        return monitor(registry, cache, cacheName, Tags.of(tags));
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()}
     * prior to building the cache for metrics to be recorded.
     * @param registry The registry to bind metrics to.
     * @param cache The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags Tags to apply to all recorded metrics.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @param <C> The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     * @see CacheStats
     */
    public static <K, V, C extends Cache<K, V>> C monitor(MeterRegistry registry, C cache, String cacheName,
            Iterable<Tag> tags) {
        new CaffeineCacheMetrics<>(cache, cacheName, tags).bindTo(registry);
        return cache;
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()}
     * prior to building the cache for metrics to be recorded.
     * @param registry The registry to bind metrics to.
     * @param cache The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags Tags to apply to all recorded metrics. Must be an even number of
     * arguments representing key/value pairs of tags.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @param <C> The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     */
    public static <K, V, C extends AsyncCache<K, V>> C monitor(MeterRegistry registry, C cache, String cacheName,
            String... tags) {
        return monitor(registry, cache, cacheName, Tags.of(tags));
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()}
     * prior to building the cache for metrics to be recorded.
     * @param registry The registry to bind metrics to.
     * @param cache The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags Tags to apply to all recorded metrics.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @param <C> The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     * @see CacheStats
     */
    public static <K, V, C extends AsyncCache<K, V>> C monitor(MeterRegistry registry, C cache, String cacheName,
            Iterable<Tag> tags) {
        monitor(registry, cache.synchronous(), cacheName, tags);
        return cache;
    }

    @Override
    protected Long size() {
        return getOrDefault(Cache::estimatedSize, null);
    }

    @Override
    protected long hitCount() {
        return getOrDefault(c -> c.stats().hitCount(), 0L);
    }

    @Override
    protected Long missCount() {
        return getOrDefault(c -> c.stats().missCount(), null);
    }

    @Override
    protected Long evictionCount() {
        return getOrDefault(c -> c.stats().evictionCount(), null);
    }

    @Override
    protected long putCount() {
        return getOrDefault(c -> c.stats().loadCount(), 0L);
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
        C cache = getCache();
        if (cache == null || !cache.policy().isRecordingStats()) {
            return;
        }

        FunctionCounter.builder("cache.eviction.weight", cache, c -> c.stats().evictionWeight())
            .tags(getTagsWithCacheName())
            .description("The sum of weights of evicted entries. This total does not include manual invalidations.")
            .register(registry);

        if (cache instanceof LoadingCache) {
            // dividing these gives you a measure of load latency
            TimeGauge.builder("cache.load.duration", cache, TimeUnit.NANOSECONDS, c -> c.stats().totalLoadTime())
                .tags(getTagsWithCacheName())
                .description("The time the cache has spent loading new values")
                .register(registry);

            FunctionCounter.builder("cache.load", cache, c -> c.stats().loadSuccessCount())
                .tags(getTagsWithCacheName())
                .tags("result", "success")
                .description(DESCRIPTION_CACHE_LOAD)
                .register(registry);

            FunctionCounter.builder("cache.load", cache, c -> c.stats().loadFailureCount())
                .tags(getTagsWithCacheName())
                .tags("result", "failure")
                .description(DESCRIPTION_CACHE_LOAD)
                .register(registry);
        }
    }

    @Nullable
    private Long getOrDefault(Function<C, Long> function, @Nullable Long defaultValue) {
        C cache = getCache();
        if (cache != null) {
            if (!cache.policy().isRecordingStats()) {
                return null;
            }

            return function.apply(cache);
        }

        return defaultValue;
    }

    private long getOrDefault(ToLongFunction<C> function, long defaultValue) {
        C cache = getCache();
        if (cache != null) {
            if (!cache.policy().isRecordingStats()) {
                return UNSUPPORTED;
            }

            return function.applyAsLong(cache);
        }

        return defaultValue;
    }

}
