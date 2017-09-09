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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.util.concurrent.TimeUnit;

/**
 * @author Jon Schneider
 */
public class GuavaCacheMetrics implements MeterBinder {
    /**
     * Record metrics on a Guava cache. You must call {@link CacheBuilder#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static <C extends Cache> C monitor(MeterRegistry registry, C cache, String name, String... tags) {
        return monitor(registry, cache, name, Tags.zip(tags));
    }

    /**
     * Record metrics on a Guava cache. You must call {@link CacheBuilder#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static <C extends Cache> C monitor(MeterRegistry registry, C cache, String name, Iterable<Tag> tags) {
        new GuavaCacheMetrics(name, tags, cache).bindTo(registry);
        return cache;
    }

    private final String name;
    private final Iterable<Tag> tags;
    private final Cache<?, ?> cache;

    public GuavaCacheMetrics(String name, Iterable<Tag> tags, Cache<?, ?> cache) {
        this.name = name;
        this.tags = tags;
        this.cache = cache;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge(registry.createId(name + ".estimated.size", tags,
            "The approximate number of entries in this cache"),
            cache, Cache::size);

        registry.more().counter(registry.createId(name + ".requests", Tags.zip("result", "miss"),
            "the number of times cache lookup methods have returned an uncached (newly loaded) value, or null"),
            cache, c -> c.stats().missCount());
        registry.more().counter(registry.createId(name + ".requests", Tags.zip("result", "hit"),
            "The number of times cache lookup methods have returned a cached value."),
            cache, c -> c.stats().hitCount());
        registry.more().counter(registry.createId(name + ".evictions", tags, "cache evictions"),
            cache, c -> c.stats().evictionCount());

        if (cache instanceof com.github.benmanes.caffeine.cache.LoadingCache) {
            // dividing these gives you a measure of load latency
            registry.more().timeGauge(registry.createId(name + ".load.duration", tags,
                "The time the cache has spent loading new values"),
                cache, TimeUnit.NANOSECONDS, c -> c.stats().totalLoadTime());

            registry.more().counter(registry.createId(name + ".load", Tags.concat(tags, "result", "success"),
                "The number of times cache lookup methods have successfully loaded a new value"),
                cache, c -> c.stats().loadSuccessCount());
            registry.more().counter(registry.createId(name + ".load", Tags.concat(tags, "result", "failure"),
                "The number of times cache lookup methods threw an exception while loading a new value"),
                cache, c -> c.stats().loadExceptionCount());
        }
    }
}
