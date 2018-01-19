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
import com.google.common.cache.LoadingCache;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import java.util.concurrent.TimeUnit;

/**
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class GuavaCacheMetrics implements MeterBinder {
    /**
     * Record metrics on a Guava cache. You must call {@link CacheBuilder#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param name     The name prefix of the metrics.
     * @param tags     Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
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
        new GuavaCacheMetrics(cache, name, tags).bindTo(registry);
        return cache;
    }

    private final String name;
    private final Iterable<Tag> tags;
    private final Cache<?, ?> cache;

    public GuavaCacheMetrics(Cache<?, ?> cache, String name, Iterable<Tag> tags) {
        this.name = name;
        this.tags = tags;
        this.cache = cache;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(name + ".estimated.size", cache, Cache::size)
            .tags(tags)
            .description("The approximate number of entries in this cache")
            .register(registry);

        FunctionCounter.builder(name + ".requests", cache, c -> c.stats().missCount())
            .tags(tags).tags("result", "miss")
            .description("The number of times cache lookup methods have returned an uncached (newly loaded) value, or null")
            .register(registry);

        FunctionCounter.builder(name + ".requests", cache, c -> c.stats().hitCount())
            .tags(tags).tags("result", "hit")
            .description("The number of times cache lookup methods have returned a cached value")
            .register(registry);

        FunctionCounter.builder(name + ".evictions", cache, c -> c.stats().evictionCount())
            .tags(tags)
            .description("Cache evictions")
            .register(registry);

        if (cache instanceof LoadingCache) {
            // dividing these gives you a measure of load latency
            TimeGauge.builder(name + ".load.duration", cache, TimeUnit.NANOSECONDS, c -> c.stats().totalLoadTime())
                .tags(tags)
                .description("The time the cache has spent loading new values")
                .register(registry);

            FunctionCounter.builder(name + ".load",cache, c -> c.stats().loadSuccessCount())
                .tags(tags).tags("result", "success")
                .description("The number of times cache lookup methods have successfully loaded a new value")
                .register(registry);

            FunctionCounter.builder(name + ".load", cache, c -> c.stats().loadExceptionCount())
                .tags(tags).tags("result", "failure")
                .description("The number of times cache lookup methods threw an exception while loading a new value")
                .register(registry);
        }
    }
}
