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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.util.List;

/* Collect metrics from Caffeine's com.github.benmanes.caffeine.cache.Cache.
  * <p>
  * <pre>{@code
  *
  * // Note that `recordStats()` is required to gather non-zero statistics
  * Cache<String, String> cache = Caffeine.newBuilder().recordStats().build();
  * CacheMetricsCollector cacheMetrics = new CacheMetricsCollector().register();
  * cacheMetrics.addCache("mycache", cache);
  *
  * }</pre>
  *
  * Exposed metrics are labeled with the provided cache name.
  *
  * With the example above, sample metric names would be:
  * <pre>
  *     caffeine_cache_hit_total{cache="mycache"} 10.0
  *     caffeine_cache_miss_total{cache="mycache"} 3.0
  *     caffeine_cache_requests_total{cache="mycache"} 13.0
  *     caffeine_cache_eviction_total{cache="mycache"} 1.0
  *     caffeine_cache_estimated_size{cache="mycache"} 5.0
  * </pre>
  *
  * Additionally if the cache includes a loader, the following metrics would be provided:
  * <pre>
  *     caffeine_cache_load_failure_total{cache="mycache"} 2.0
  *     caffeine_cache_loads_total{cache="mycache"} 7.0
  *     caffeine_cache_load_duration_seconds_count{cache="mycache"} 7.0
  *     caffeine_cache_load_duration_seconds_sum{cache="mycache"} 0.0034
  * </pre>
  *
  * @author Clint Checketts
 */
public class CaffeineCacheMetrics implements MeterBinder {
  private final String name;
  private final Cache<?, ?> cache;

  /**
   * @param name  The named of the cache, exposed as the 'cache' dimension
   * @param cache The cache to be instrumented. Be certain to enable <cade></cade>
   */
  public CaffeineCacheMetrics(String name, Cache<?, ?> cache) {
    this.name = name;
    this.cache = cache;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    List<Tag> tags = Tags.zip("cache", name);
    registry.gauge("caffeine_cache_estimated_size", tags, cache, Cache::estimatedSize);

    registry.counter("caffeine_cache_requests", Tags.zip("cache", name, "result", "miss"), cache, c -> c.stats().missCount());
    registry.counter("caffeine_cache_requests", Tags.zip("cache", name, "result", "hit"), cache, c -> c.stats().hitCount());
    registry.counter("caffeine_cache_requests_total", tags, cache, c -> c.stats().requestCount());
    registry.counter("caffeine_cache_evictions_total", tags, cache, c -> c.stats().evictionCount());
    registry.gauge("caffeine_cache_eviction_weight", tags, cache, c -> c.stats().evictionWeight());

    if (cache instanceof LoadingCache) {
      // dividing these gives you a measure of load latency
      registry.counter("caffeine_cache_load_duration_seconds_sum", tags, cache, c -> c.stats().totalLoadTime());
      registry.counter("caffeine_cache_load_duration_seconds_count", tags, cache, c -> c.stats().loadCount());

      registry.counter("caffeine_cache_load_failures_total", tags, cache, c -> c.stats().loadFailureCount());
    }
  }
}
