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
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static io.micrometer.core.instrument.Meter.Type.Counter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Clint Checketts
 */
class CaffeineCacheMetricsTest {

    @Test
    public void cacheExposesMetricsForHitMissAndEviction() throws Exception {
        Cache<String, String> cache = Caffeine.newBuilder().maximumSize(2).recordStats().executor(new Executor() {
            @Override
            public void execute(Runnable command) {
                // Run cleanup in same thread, to remove async behavior with evictions
                command.run();
            }
        }).build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        CaffeineCacheMetrics.monitor(registry, cache, "users");

        cache.getIfPresent("user1");
        cache.getIfPresent("user1");
        cache.put("user1", "First User");
        cache.getIfPresent("user1");

        // Add to cache to trigger eviction.
        cache.put("user2", "Second User");
        cache.put("user3", "Third User");
        cache.put("user4", "Fourth User");

        assertMetric(registry, Counter, Tags.zip("cache", "users", "result", "hit"), "users", 1.0, "caffeine_cache_requests");
        assertMetric(registry, Counter, Tags.zip("cache", "users", "result", "miss"), "users", 2.0, "caffeine_cache_requests");
        assertMetric(registry, Counter, "users", 3.0, "caffeine_cache_requests_total");
        assertMetric(registry, Counter, "users", 2.0, "caffeine_cache_evictions_total");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void loadingCacheExposesMetricsForLoadsAndExceptions() throws Exception {
        CacheLoader<String, String> loader = mock(CacheLoader.class);
        when(loader.load(anyString()))
            .thenReturn("First User")
            .thenThrow(new RuntimeException("Seconds time fails"))
            .thenReturn("Third User");

        LoadingCache<String, String> cache = Caffeine.newBuilder().recordStats().build(loader);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        CaffeineCacheMetrics.monitor(registry, cache, "loadingusers");

        cache.get("user1");
        cache.get("user1");
        try {
            cache.get("user2");
        } catch (Exception e) {
            // ignoring.
        }
        cache.get("user3");

        assertMetric(registry, Counter, Tags.zip("cache", "loadingusers", "result", "hit"), "loadingusers", 1.0, "caffeine_cache_requests");
        assertMetric(registry, Counter, Tags.zip("cache", "loadingusers", "result", "miss"), "loadingusers", 3.0, "caffeine_cache_requests");

        assertMetric(registry, Counter, "loadingusers", 1.0, "caffeine_cache_load_failures_total");
        assertMetric(registry, Counter, "loadingusers", 3.0, "caffeine_cache_load_duration_seconds_count");

        assertMetric(registry, Counter, "loadingusers", 3.0, "caffeine_cache_load_duration_seconds_count");
    }

    private void assertMetric(MeterRegistry registry, Meter.Type type, String cacheName, double value, String name) {
        assertMetric(registry, type, Tags.zip("cache", cacheName), cacheName, value, name);
    }

    private void assertMetric(MeterRegistry registry, Meter.Type type, List<Tag> tags, String cacheName, double value, String name) {
        Optional<Meter> meter = registry.findMeter(type, name, tags);
        if (!meter.isPresent()) {
            System.out.println("boom");
        }
        assertThat(meter).as("Meter should be in registry type=" + type + " tags=" + tags + " metricName=").isPresent();

        assertThat(meter.get().measure().stream().findFirst().map(Measurement::getValue).get()).isEqualTo(value);
    }
}
