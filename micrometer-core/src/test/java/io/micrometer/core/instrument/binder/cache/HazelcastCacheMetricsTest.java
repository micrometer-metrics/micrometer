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

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IMap;
import com.hazelcast.monitor.NearCacheStats;
import com.hazelcast.monitor.impl.LocalMapStatsImpl;
import com.hazelcast.monitor.impl.NearCacheStatsImpl;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HazelcastCacheMetrics}.
 *
 * @author Oleksii Bondar
 */
class HazelcastCacheMetricsTest extends AbstractCacheMetricsTest {

    private static IMap<String, String> cache;

    private Tags expectedTag = Tags.of("app", "test");
    private HazelcastCacheMetrics metrics = new HazelcastCacheMetrics(cache, expectedTag);

    @Test
    void reportMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics.bindTo(meterRegistry);

        verifyCommonCacheMetrics(meterRegistry);

        meterRegistry.get("cache.entries").tags(expectedTag).tag("ownership", "backup").gauge();
        meterRegistry.get("cache.entries").tags(expectedTag).tag("ownership", "owned").gauge();
        meterRegistry.get("cache.entry.memory").tags(expectedTag).tag("ownership", "backup").gauge();
        meterRegistry.get("cache.entry.memory").tags(expectedTag).tag("ownership", "owned").gauge();
        meterRegistry.get("cache.partition.gets").tags(expectedTag).functionCounter();

        // near cache stats
        meterRegistry.get("cache.near.requests").tags(expectedTag).tag("result", "hit").gauge();
        meterRegistry.get("cache.near.requests").tags(expectedTag).tag("result", "miss").gauge();
        meterRegistry.get("cache.near.evictions").tags(expectedTag).gauge();
        meterRegistry.get("cache.near.persistences").tags(expectedTag).gauge();

        // timings
        meterRegistry.get("cache.gets.latency").tags(expectedTag).functionTimer();
        meterRegistry.get("cache.puts.latency").tags(expectedTag).functionTimer();
        meterRegistry.get("cache.removals.latency").tags(expectedTag).functionTimer();
    }
    
    @Test
    void constructInstanceViaStaticMethodMonitor() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        HazelcastCacheMetrics.monitor(meterRegistry, cache, expectedTag);

        meterRegistry.get("cache.partition.gets").tags(expectedTag).functionCounter();
    }

    @Test
    void returnCacheSize() {
        assertThat(metrics.size()).isEqualTo(cache.size());
    }

    @Test
    void returnNullForMissCount() {
        assertThat(metrics.missCount()).isNull();
    }

    @Test
    void returnNullForEvictionCount() {
        assertThat(metrics.evictionCount()).isNull();
    }

    @Test
    void returnHitCount() {
        assertThat(metrics.hitCount()).isEqualTo(cache.getLocalMapStats().getHits());
    }

    @Test
    void returnPutCount() {
        assertThat(metrics.putCount()).isEqualTo(cache.getLocalMapStats().getPutOperationCount());
    }

    @BeforeAll
    static void setup() {
        cache = Hazelcast.newHazelcastInstance().getMap("mycache");
        NearCacheStats nearCacheStats = new NearCacheStatsImpl();
        ((LocalMapStatsImpl) cache.getLocalMapStats()).setNearCacheStats(nearCacheStats);
    }

    @AfterAll
    static void cleanup() {
        Hazelcast.shutdownAll();
    }

}
