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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Clint Checketts
 * @author Jon Schneider
 */
class CaffeineCacheMetricsTest {
    private SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private Iterable<Tag> userTags = Tags.zip("userTagKey", "userTagValue");

    @Test
    void cacheExposesMetricsForHitMissAndEviction() throws Exception {
        // Run cleanup in same thread, to remove async behavior with evictions
        Cache<String, String> cache = Caffeine.newBuilder().maximumSize(2).recordStats().executor(Runnable::run).build();
        CaffeineCacheMetrics.monitor(registry, cache, "c", userTags);

        cache.getIfPresent("user1");
        cache.getIfPresent("user1");
        cache.put("user1", "First User");
        cache.getIfPresent("user1");

        // Add to cache to trigger eviction.
        cache.put("user2", "Second User");
        cache.put("user3", "Third User");
        cache.put("user4", "Fourth User");

        assertThat(registry.find("c.requests").tags("result", "hit").tags(userTags).functionCounter().map(FunctionCounter::count)).hasValue(1.0);
        assertThat(registry.find("c.requests").tags("result", "miss").tags(userTags).functionCounter().map(FunctionCounter::count)).hasValue(2.0);
        assertThat(registry.find("c.evictions").tags(userTags).functionCounter().map(FunctionCounter::count)).hasValue(2.0);
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
            }), "c", userTags);

        cache.get(1);
        cache.get(1);
        try {
            cache.get(2); // throws exception
        } catch (Exception ignored) {
        }
        cache.get(3);

        assertThat(registry.find("c.requests").tags("result", "hit").tags(userTags).functionCounter().map(FunctionCounter::count)).hasValue(1.0);
        assertThat(registry.find("c.requests").tags("result", "miss").tags(userTags).functionCounter().map(FunctionCounter::count)).hasValue(3.0);
        assertThat(registry.find("c.load").tags("result", "failure").functionCounter().map(FunctionCounter::count)).hasValue(1.0);
        assertThat(registry.find("c.load").tags("result", "success").functionCounter().map(FunctionCounter::count)).hasValue(2.0);
    }
}
