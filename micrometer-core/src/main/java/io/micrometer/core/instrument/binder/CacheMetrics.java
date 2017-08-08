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
import com.google.common.cache.LoadingCache;
import io.micrometer.core.instrument.*;

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

        registry.counter(name + "_requests", Tags.zip("result", "miss"), cache, c -> c.stats().missCount());
        registry.counter(name + "_requests", Tags.zip("result", "hit"), cache, c -> c.stats().hitCount());
        registry.counter(name + "_evictions", tags, cache, c -> c.stats().evictionCount());

        if (cache instanceof LoadingCache) {
            // dividing these gives you a measure of load latency
            registry.counter(name + "_load_duration", tags, cache, c -> c.stats().totalLoadTime());
            registry.counter(name + "_loads", tags, cache, c -> c.stats().loadCount());

            registry.counter(name + "_load_failures", tags, cache, c -> c.stats().loadExceptionCount());
        }
    }
}
