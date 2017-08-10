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
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Clint Checketts
 * @author Jon Schneider
 */
class CaffeineCacheMetricsTest {
    private SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void cacheExposesMetricsForHitMissAndEviction() throws Exception {
        // Run cleanup in same thread, to remove async behavior with evictions
        Cache<String, String> cache = Caffeine.newBuilder().maximumSize(2).recordStats().executor(Runnable::run).build();
        CaffeineCacheMetrics.monitor(registry, cache, "c");

        cache.getIfPresent("user1");
        cache.getIfPresent("user1");
        cache.put("user1", "First User");
        cache.getIfPresent("user1");

        // Add to cache to trigger eviction.
        cache.put("user2", "Second User");
        cache.put("user3", "Third User");
        cache.put("user4", "Fourth User");

        assertThat(findCounter("c_requests", "result", "hit")).hasValueSatisfying(c -> val(c, 1));
        assertThat(findCounter("c_requests", "result", "miss")).hasValueSatisfying(c -> val(c, 2));
        assertThat(findCounter("c_evictions")).hasValueSatisfying(c -> val(c, 2));
    }

    @SuppressWarnings("unchecked")
    @Test
    void loadingCacheExposesMetricsForLoadsAndExceptions() throws Exception {
        LoadingCache<Integer, String> cache = CaffeineCacheMetrics.monitor(registry, Caffeine.newBuilder()
            .recordStats()
            .build(key -> {
                if (key % 2 == 0)
                    throw new Exception("no evens!");
                return key.toString();
            }), "c");

        cache.get(1);
        cache.get(1);
        try {
            cache.get(2); // throws exception
        } catch (Exception ignored) {
        }
        cache.get(3);

        assertThat(findCounter("c_requests", "result", "hit")).hasValueSatisfying(c -> val(c, 1));
        assertThat(findCounter("c_requests", "result", "miss")).hasValueSatisfying(c -> val(c, 3));
        assertThat(findCounter("c_load", "result", "failure")).hasValueSatisfying(c -> val(c, 1));
        assertThat(findCounter("c_load", "result", "success")).hasValueSatisfying(c -> val(c, 2));
    }

    private Optional<Meter> findCounter(String name, String... tags) {
        return registry.findMeter(Meter.Type.Counter, name, tags);
    }

    private void val(Meter m, double value) {
        assertThat(m.measure().get(0).getValue()).isEqualTo(value);
    }
}
