/*
 * Copyright 2018 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.cache;

import com.google.common.cache.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GuavaCacheMetrics}.
 *
 * @author Oleksii Bondar
 * @author Johnny Lim
 */
class GuavaCacheMetricsTest extends AbstractCacheMetricsTest {

    // tag::setup[]
    LoadingCache<String, String> cache = CacheBuilder.newBuilder().recordStats().build(new CacheLoader<>() {
        public String load(String key) {
            return "";
        }
    });

    GuavaCacheMetrics<String, String, Cache<String, String>> metrics = new GuavaCacheMetrics<>(cache, "testCache",
            expectedTag);

    // end::setup[]

    @Test
    void reportExpectedMetrics() throws ExecutionException {
        cache.put("a", "1");
        cache.get("a");
        cache.get("a");
        cache.get("b");

        // tag::register[]
        MeterRegistry registry = new SimpleMeterRegistry();
        metrics.bindTo(registry);
        // end::register[]

        verifyCommonCacheMetrics(registry, metrics);

        // common metrics
        Gauge cacheSize = fetch(registry, "cache.size").gauge();
        assertThat(cacheSize.value()).isEqualTo(cache.size()).isEqualTo(2);

        FunctionCounter hitCount = fetch(registry, "cache.gets", Tags.of("result", "hit")).functionCounter();
        assertThat(hitCount.count()).isEqualTo(metrics.hitCount()).isEqualTo(2);

        FunctionCounter missCount = fetch(registry, "cache.gets", Tags.of("result", "miss")).functionCounter();
        assertThat(missCount.count()).isEqualTo(metrics.missCount().doubleValue()).isEqualTo(1);

        FunctionCounter cachePuts = fetch(registry, "cache.puts").functionCounter();
        assertThat(cachePuts.count()).isEqualTo(metrics.putCount());

        FunctionCounter cacheEviction = fetch(registry, "cache.evictions").functionCounter();
        assertThat(cacheEviction.count()).isEqualTo(metrics.evictionCount().doubleValue());

        CacheStats stats = cache.stats();
        TimeGauge loadDuration = fetch(registry, "cache.load.duration").timeGauge();
        assertThat(loadDuration.value(TimeUnit.NANOSECONDS)).isEqualTo(stats.totalLoadTime());

        FunctionCounter successfulLoad = fetch(registry, "cache.load", Tags.of("result", "success")).functionCounter();
        assertThat(successfulLoad.count()).isEqualTo(stats.loadSuccessCount());

        FunctionCounter failedLoad = fetch(registry, "cache.load", Tags.of("result", "failure")).functionCounter();
        assertThat(failedLoad.count()).isEqualTo(stats.loadExceptionCount());
    }

    @Test
    void constructInstanceViaStaticMethodMonitor() {
        // tag::monitor[]
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        GuavaCacheMetrics.monitor(meterRegistry, cache, "testCache", expectedTag);
        // end::monitor[]

        meterRegistry.get("cache.load.duration").tags(expectedTag).timeGauge();
    }

    @Test
    void returnCacheSize() {
        assertThat(metrics.size()).isEqualTo(cache.size());
    }

    @Test
    void returnHitCount() throws ExecutionException {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics.bindTo(meterRegistry);

        cache.put("a", "1");
        cache.get("a");
        cache.get("a");

        assertThat(metrics.hitCount()).isEqualTo(cache.stats().hitCount()).isEqualTo(2);
        assertThat(meterRegistry.get("cache.gets").tag("result", "hit").functionCounter().count()).isEqualTo(2);
    }

    @Test
    void returnHitCountWithoutRecordStats() throws ExecutionException {
        LoadingCache<String, String> cache = CacheBuilder.newBuilder().build(new CacheLoader<>() {
            public String load(String key) {
                return "";
            }
        });
        GuavaCacheMetrics<String, String, Cache<String, String>> metrics = new GuavaCacheMetrics<>(cache, "testCache",
                expectedTag);

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics.bindTo(meterRegistry);

        cache.put("a", "1");
        cache.get("a");
        cache.get("a");

        assertThat(metrics.hitCount()).isEqualTo(cache.stats().hitCount()).isEqualTo(0);
        assertThat(meterRegistry.get("cache.gets").tag("result", "hit").functionCounter().count()).isEqualTo(0);
    }

    @Test
    void returnMissCount() throws ExecutionException {
        cache.get("b");

        assertThat(metrics.missCount()).isEqualTo(cache.stats().missCount()).isEqualTo(1);
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
