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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.testsupport.system.CapturedOutput;
import io.micrometer.core.testsupport.system.OutputCaptureExtension;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link CaffeineCacheMetrics}.
 *
 * @author Oleksii Bondar
 * @author Johnny Lim
 */
@ExtendWith(OutputCaptureExtension.class)
class CaffeineCacheMetricsTest extends AbstractCacheMetricsTest {

    // tag::setup[]
    LoadingCache<String, String> cache = Caffeine.newBuilder().recordStats().build(key -> "");

    CaffeineCacheMetrics<String, String, Cache<String, String>> metrics = new CaffeineCacheMetrics<>(cache, "testCache",
            expectedTag);

    // end::setup[]

    @Test
    void reportExpectedGeneralMetrics() {
        cache.put("a", "1");
        cache.get("a");
        cache.get("a");
        cache.get("b");

        // tag::register[]
        MeterRegistry registry = new SimpleMeterRegistry();
        metrics.bindTo(registry);
        // end::register[]

        verifyCommonCacheMetrics(registry, metrics);

        FunctionCounter evictionWeight = fetch(registry, "cache.eviction.weight").functionCounter();
        CacheStats stats = cache.stats();
        assertThat(evictionWeight.count()).isEqualTo((double) stats.evictionWeight());

        // specific to LoadingCache instance
        TimeGauge loadDuration = fetch(registry, "cache.load.duration").timeGauge();
        assertThat(loadDuration.value(TimeUnit.NANOSECONDS)).isCloseTo((double) stats.totalLoadTime(),
                Offset.offset(0.1));

        FunctionCounter successfulLoad = fetch(registry, "cache.load", Tags.of("result", "success")).functionCounter();
        assertThat(successfulLoad.count()).isEqualTo((double) stats.loadSuccessCount());

        FunctionCounter failedLoad = fetch(registry, "cache.load", Tags.of("result", "failure")).functionCounter();
        assertThat(failedLoad.count()).isEqualTo((double) stats.loadFailureCount());
    }

    @Test
    void constructInstanceViaStaticMethodMonitor() {
        // tag::monitor[]
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "testCache", expectedTag);
        // end::monitor[]

        meterRegistry.get("cache.eviction.weight").tags(expectedTag).functionCounter();
    }

    @Test
    void doNotReportMetricsForNonLoadingCache(CapturedOutput output) {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        Cache<Object, Object> cache = Caffeine.newBuilder().recordStats().build();
        CaffeineCacheMetrics<Object, Object, Cache<Object, Object>> metrics = new CaffeineCacheMetrics<>(cache,
                "testCache", expectedTag);
        metrics.bindTo(meterRegistry);

        assertThat(meterRegistry.find("cache.load.duration").timeGauge()).isNull();
        assertThat(output).doesNotContain(
                "The cache 'testCache' is not recording statistics. Only meters that do not require cache statistics will be registered. Call 'Caffeine#recordStats()' prior to building the cache for metrics to be recorded.");
    }

    @Test
    void returnCacheSize() {
        assertThat(metrics.size()).isEqualTo(cache.estimatedSize());
    }

    @Test
    void returnCacheSizeWithoutRecordStats(CapturedOutput output) {
        LoadingCache<String, String> cache = Caffeine.newBuilder().build(key -> "");
        CaffeineCacheMetrics<String, String, Cache<String, String>> metrics = new CaffeineCacheMetrics<>(cache,
                "testCache", Tags.empty());

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics.bindTo(meterRegistry);

        cache.put("a", "1");
        assertThat(metrics.size()).isOne();
        assertThat(meterRegistry.get("cache.size").gauge().value()).isOne();
        assertThat(output).contains(
                "The cache 'testCache' is not recording statistics. Only meters that do not require cache statistics will be registered. Call 'Caffeine#recordStats()' prior to building the cache for metrics to be recorded.");
    }

    @Test
    void reportUtilizationForBoundedCache() {
        Cache<String, String> cache = Caffeine.newBuilder().maximumSize(10).recordStats().build();
        cache.put("a", "1");
        cache.put("b", "2");

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "testCache", expectedTag);

        Gauge utilization = meterRegistry.get("cache.utilization").tags(expectedTag).gauge();
        assertThat(utilization.value()).isEqualTo(20.0);
        assertThat(utilization.getId().getBaseUnit()).isEqualTo(BaseUnits.PERCENT);
    }

    @Test
    void reportUtilizationForWeightedCache() {
        Cache<String, String> cache = Caffeine.newBuilder()
            .maximumWeight(10)
            .weigher((String key, String value) -> value.length())
            .recordStats()
            .build();
        cache.put("a", "123");
        cache.put("b", "12");

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "testCache", expectedTag);

        assertThat(meterRegistry.get("cache.utilization").tags(expectedTag).gauge().value()).isEqualTo(50.0);
    }

    @Test
    void doesNotReportUtilizationForUnboundedCache() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "testCache", expectedTag);

        assertThat(meterRegistry.find("cache.utilization").tags(expectedTag).gauge()).isNull();
    }

    @Test
    void reportUtilizationWithoutRecordStats(CapturedOutput output) {
        Cache<String, String> cache = Caffeine.newBuilder().maximumSize(10).build();
        cache.put("a", "1");

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "testCache", expectedTag);

        assertThat(meterRegistry.get("cache.utilization").tags(expectedTag).gauge().value()).isEqualTo(10.0);
        assertThat(output).contains(
                "The cache 'testCache' is not recording statistics. Only meters that do not require cache statistics will be registered. Call 'Caffeine#recordStats()' prior to building the cache for metrics to be recorded.");
    }

    @Test
    void returnHitCount() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics.bindTo(meterRegistry);

        cache.put("a", "1");
        cache.get("a");
        cache.get("a");

        assertThat(metrics.hitCount()).isEqualTo(cache.stats().hitCount()).isEqualTo(2);
        assertThat(meterRegistry.get("cache.gets").tag("result", "hit").functionCounter().count()).isEqualTo(2);
    }

    @Test
    void returnHitCountWithoutRecordStats() {
        LoadingCache<String, String> cache = Caffeine.newBuilder().build(key -> "");
        CaffeineCacheMetrics<String, String, Cache<String, String>> metrics = new CaffeineCacheMetrics<>(cache,
                "testCache", expectedTag);

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics.bindTo(meterRegistry);

        cache.put("a", "1");
        cache.get("a");
        cache.get("a");

        assertThat(cache.stats().hitCount()).isEqualTo(0);
        assertThat(metrics.hitCount()).isEqualTo(CaffeineCacheMetrics.UNSUPPORTED);
        assertThatExceptionOfType(MeterNotFoundException.class)
            .isThrownBy(() -> meterRegistry.get("cache.gets").tag("result", "hit").functionCounter());
    }

    @Test
    void returnMissCount() {
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
