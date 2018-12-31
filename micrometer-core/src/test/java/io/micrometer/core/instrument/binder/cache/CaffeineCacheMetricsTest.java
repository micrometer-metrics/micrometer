/**
 * Copyright 2018 Pivotal Software, Inc.
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
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link CaffeineCacheMetrics}.
 *
 * @author Oleksii Bondar
 */
class CaffeineCacheMetricsTest extends AbstractCacheMetricsTest {

    private Tags expectedTag = Tags.of("app", "test");
    private LoadingCache<String, String> cache = Caffeine.newBuilder().build(new CacheLoader<String, String>() {
        public String load(String key) throws Exception {
            return "";
        };
    });
    private CaffeineCacheMetrics metrics = new CaffeineCacheMetrics(cache, "testCache", expectedTag);

    @Test
    void reportExpectedGeneralMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics.bindTo(meterRegistry);

        meterRegistry.get("cache.eviction.weight").tags(expectedTag).gauge();

        // specific to LoadingCache instance
        meterRegistry.get("cache.load.duration").tags(expectedTag).timeGauge();
        meterRegistry.get("cache.load").tags(expectedTag).tag("result", "success").functionCounter();
        meterRegistry.get("cache.load").tags(expectedTag).tag("result", "failure").functionCounter();
    }
    
    @Test
    void doNotReportMetricsForNonLoadingCache() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Cache<Object, Object> cache = Caffeine.newBuilder().build();
        CaffeineCacheMetrics metrics = new CaffeineCacheMetrics(cache, "testCache", expectedTag);
        metrics.bindTo(meterRegistry);

        assertThrows(MeterNotFoundException.class, () -> {
            meterRegistry.get("cache.load.duration").tags(expectedTag).timeGauge();
        });
    }

    @Test
    void returnCacheSize() {
        assertThat(metrics.size()).isEqualTo(cache.estimatedSize());
    }

    @Test
    void returnHitCount() {
        assertThat(metrics.hitCount()).isEqualTo(cache.stats().hitCount());
    }

    @Test
    void returnMissCount() {
        assertThat(metrics.missCount()).isEqualTo(cache.stats().missCount());
    }

    @Test
    void returnEvictionCount() {
        assertThat(metrics.evictionCount()).isEqualTo(cache.stats().evictionCount());
    }

    @Test
    void returnPutCount() {
        assertThat(metrics.putCount()).isEqualTo(cache.stats().loadCount());
    }

}
