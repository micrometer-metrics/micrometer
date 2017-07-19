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
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import io.micrometer.core.instrument.*;

import java.util.Arrays;

import static java.util.Collections.singletonList;

/**
 * @author Jon Schneider
 */
public class CacheMetrics implements MeterBinder {
    private final String name;
    private final Iterable<Tag> tags;
    private final Cache<?, ?> cache;

    public CacheMetrics(String name, Iterable<Tag> tags, Cache<?, ?> cache) {
        this.name = name;
        this.tags = tags;
        this.cache = cache;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge(name + "_size", tags, cache, Cache::size);

        registry.register(Meters.build(name + "_requests")
                .type(Meter.Type.Counter)
                .create(cache, (n, cacheRef) -> {
                    CacheStats stats = cacheRef.stats();
                    return Arrays.asList(
                            /**
                             * The sum of these two measurements is equal to {@link CacheStats#requestCount()}
                             */
                            new Measurement(n, singletonList(Tag.of("result", "hit")), stats.hitCount()),
                            new Measurement(n, singletonList(Tag.of("result", "miss")), stats.missCount())
                    );
                }));

        registry.gauge(name + "_evictions", tags, cache, c -> c.stats().evictionCount());
        registry.gauge(name + "_load_duration", tags, cache, c -> c.stats().totalLoadTime());

        if (cache instanceof LoadingCache) {
            registry.gauge(name + "_loads", tags, cache, c -> c.stats().loadCount());
            registry.gauge(name + "_load_failures", tags, cache, c -> c.stats().loadExceptionCount());
        }
    }
}