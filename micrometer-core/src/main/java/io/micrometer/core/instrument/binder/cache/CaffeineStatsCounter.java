/*
 * Copyright 2021 VMware, Inc.
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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.instrument.*;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * A {@link StatsCounter} instrumented with Micrometer. This will provide more detailed
 * metrics than using {@link CaffeineCacheMetrics}.
 * <p>
 * Note that this doesn't instrument the cache's size by default. Use
 * {@link #registerSizeMetric(Cache)} to do so after the cache has been built.
 * <p>
 * Use {@link com.github.benmanes.caffeine.cache.Caffeine#recordStats} to supply this
 * class to the cache builder: <pre>{@code
 * MeterRegistry registry = ...;
 * Cache<Key, Graph> graphs = Caffeine.newBuilder()
 *     .maximumSize(10_000)
 *     .recordStats(() -> new CaffeineStatsCounter(registry, "graphs"))
 *     .build();
 * }</pre>
 *
 * @author Ben Manes
 * @author John Karp
 * @author Johnny Lim
 * @see CaffeineCacheMetrics
 * @since 1.7.0
 */
@NonNullApi
@NonNullFields
public final class CaffeineStatsCounter implements StatsCounter {

    private static final String DESCRIPTION_CACHE_GETS = "The number of times cache lookup methods have returned a cached (hit) or uncached (newly loaded) value (miss).";

    private static final String DESCRIPTION_CACHE_LOADS = "The number of times cache lookup methods have successfully loaded a new value or failed to load a new value, either because no value was found or an exception was thrown while loading";

    private final MeterRegistry registry;

    private final Tags tags;

    private final Counter hitCount;

    private final Counter missCount;

    private final Timer loadSuccesses;

    private final Timer loadFailures;

    private final EnumMap<RemovalCause, DistributionSummary> evictionMetrics;

    /**
     * Constructs an instance for use by a single cache.
     * @param registry the registry of metric instances
     * @param cacheName will be used to tag metrics with "cache".
     */
    public CaffeineStatsCounter(MeterRegistry registry, String cacheName) {
        this(registry, cacheName, Tags.empty());
    }

    /**
     * Constructs an instance for use by a single cache.
     * @param registry the registry of metric instances
     * @param cacheName will be used to tag metrics with "cache".
     * @param extraTags tags to apply to all recorded metrics.
     */
    public CaffeineStatsCounter(MeterRegistry registry, String cacheName, Iterable<Tag> extraTags) {
        requireNonNull(registry);
        requireNonNull(cacheName);
        requireNonNull(extraTags);
        this.registry = registry;
        this.tags = Tags.concat(extraTags, "cache", cacheName);

        hitCount = Counter.builder("cache.gets")
            .tag("result", "hit")
            .tags(tags)
            .description(DESCRIPTION_CACHE_GETS)
            .register(registry);
        missCount = Counter.builder("cache.gets")
            .tag("result", "miss")
            .tags(tags)
            .description(DESCRIPTION_CACHE_GETS)
            .register(registry);
        loadSuccesses = Timer.builder("cache.loads")
            .tag("result", "success")
            .tags(tags)
            .description(DESCRIPTION_CACHE_LOADS)
            .register(registry);
        loadFailures = Timer.builder("cache.loads")
            .tag("result", "failure")
            .tags(tags)
            .description(DESCRIPTION_CACHE_LOADS)
            .register(registry);

        evictionMetrics = new EnumMap<>(RemovalCause.class);
        Arrays.stream(RemovalCause.values())
            .forEach(cause -> evictionMetrics.put(cause,
                    DistributionSummary.builder("cache.evictions")
                        .tag("cause", cause.name())
                        .tags(tags)
                        .description("The number of times the cache was evicted.")
                        .register(registry)));
    }

    /**
     * Register a gauge for the size of the given cache.
     * @param cache cache to register a gauge for its size
     */
    public void registerSizeMetric(Cache<?, ?> cache) {
        Gauge.builder("cache.size", cache, Cache::estimatedSize)
            .tags(tags)
            .description("The approximate number of entries in this cache.")
            .register(registry);
    }

    @Override
    public void recordHits(int count) {
        hitCount.increment(count);
    }

    @Override
    public void recordMisses(int count) {
        missCount.increment(count);
    }

    @Override
    public void recordLoadSuccess(long loadTime) {
        loadSuccesses.record(loadTime, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordLoadFailure(long loadTime) {
        loadFailures.record(loadTime, TimeUnit.NANOSECONDS);
    }

    @SuppressWarnings("deprecation")
    public void recordEviction() {
    }

    @Override
    public void recordEviction(int weight, RemovalCause cause) {
        evictionMetrics.get(cause).record(weight);
    }

    @Override
    public CacheStats snapshot() {
        return CacheStats.of((long) hitCount.count(), (long) missCount.count(), loadSuccesses.count(),
                loadFailures.count(),
                (long) loadSuccesses.totalTime(TimeUnit.NANOSECONDS)
                        + (long) loadFailures.totalTime(TimeUnit.NANOSECONDS),
                evictionMetrics.values().stream().mapToLong(DistributionSummary::count).sum(),
                (long) evictionMetrics.values().stream().mapToDouble(DistributionSummary::totalAmount).sum());
    }

    @Override
    public String toString() {
        return snapshot().toString();
    }

}
