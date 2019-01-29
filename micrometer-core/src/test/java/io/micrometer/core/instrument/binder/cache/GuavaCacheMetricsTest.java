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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GuavaCacheMetrics}.
 *
 * @author Oleksii Bondar
 */
class GuavaCacheMetricsTest extends AbstractCacheMetricsTest {

    private LoadingCache<String, String> cache = CacheBuilder.newBuilder().build(new CacheLoader<String, String>() {
        public String load(String key) throws Exception {
            return "";
        }
    });
    private GuavaCacheMetrics metrics = new GuavaCacheMetrics(cache, "testCache", expectedTag);

    @Test
    void reportExpectedMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        metrics.bindTo(registry);

        verifyCommonCacheMetrics(registry, metrics);

        // common metrics
        Gauge cacheSize = fetch(registry, "cache.size").gauge();
        assertThat(cacheSize.value()).isEqualTo(cache.size());

        FunctionCounter hitCount = fetch(registry, "cache.gets", Tags.of("result", "hit")).functionCounter();
        assertThat(hitCount.count()).isEqualTo(metrics.hitCount());

        FunctionCounter missCount = fetch(registry, "cache.gets", Tags.of("result", "miss")).functionCounter();
        assertThat(missCount.count()).isEqualTo(metrics.missCount().doubleValue());

        FunctionCounter cachePuts = fetch(registry, "cache.puts").functionCounter();
        assertThat(cachePuts.count()).isEqualTo(metrics.putCount());

        FunctionCounter cacheEviction = fetch(registry, "cache.evictions").functionCounter();
        assertThat(cacheEviction.count()).isEqualTo(metrics.evictionCount().doubleValue());

        CacheStats stats = cache.stats();
        TimeGauge loadDuration = fetch(registry, "cache.load.duration").timeGauge();
        assertThat(loadDuration.value()).isEqualTo(stats.totalLoadTime());

        FunctionCounter successfulLoad = fetch(registry, "cache.load", Tags.of("result", "success")).functionCounter();
        assertThat(successfulLoad.count()).isEqualTo(stats.loadSuccessCount());

        FunctionCounter failedLoad = fetch(registry, "cache.load", Tags.of("result", "failure")).functionCounter();
        assertThat(failedLoad.count()).isEqualTo(stats.loadExceptionCount());
    }

    @Test
    void constructInstanceViaStaticMethodMonitor() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        GuavaCacheMetrics.monitor(meterRegistry, cache, "testCache", expectedTag);

        meterRegistry.get("cache.load.duration").tags(expectedTag).timeGauge();
    }

    @Test
    void returnCacheSize() {
        assertThat(metrics.size()).isEqualTo(cache.size());
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
