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
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.monitor.NearCacheStats;
import com.hazelcast.monitor.impl.LocalMapStatsImpl;
import com.hazelcast.monitor.impl.NearCacheStatsImpl;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        LocalMapStats localMapStats = cache.getLocalMapStats();

        Gauge backupEntries = fetch(meterRegistry, "cache.entries", Tags.of("ownership", "backup")).gauge();
        assertThat(backupEntries.value()).isEqualTo(localMapStats.getBackupEntryCount());

        Gauge ownedEntries = fetch(meterRegistry, "cache.entries", Tags.of("ownership", "owned")).gauge();
        assertThat(ownedEntries.value()).isEqualTo(localMapStats.getOwnedEntryCount());

        Gauge backupEntryMemory = fetch(meterRegistry, "cache.entry.memory", Tags.of("ownership", "backup")).gauge();
        assertThat(backupEntryMemory.value()).isEqualTo(localMapStats.getBackupEntryMemoryCost());

        Gauge ownedEntryMemory = fetch(meterRegistry, "cache.entry.memory", Tags.of("ownership", "owned")).gauge();
        assertThat(ownedEntryMemory.value()).isEqualTo(localMapStats.getOwnedEntryMemoryCost());

        FunctionCounter partitionGets = fetch(meterRegistry, "cache.partition.gets").functionCounter();
        assertThat(partitionGets.count()).isEqualTo(localMapStats.getGetOperationCount());

        // near cache stats
        NearCacheStats nearCacheStats = localMapStats.getNearCacheStats();
        Gauge hitCacheRequests = fetch(meterRegistry, "cache.near.requests", Tags.of("result", "hit")).gauge();
        assertThat(hitCacheRequests.value()).isEqualTo(nearCacheStats.getHits());

        Gauge missCacheRequests = fetch(meterRegistry, "cache.near.requests", Tags.of("result", "miss")).gauge();
        assertThat(missCacheRequests.value()).isEqualTo(nearCacheStats.getMisses());

        Gauge nearPersistance = fetch(meterRegistry, "cache.near.persistences").gauge();
        assertThat(nearPersistance.value()).isEqualTo(nearCacheStats.getPersistenceCount());

        Gauge nearEvictions = fetch(meterRegistry, "cache.near.evictions").gauge();
        assertThat(nearEvictions.value()).isEqualTo(nearCacheStats.getEvictions());

        // timings
        FunctionTimer getsLatency = fetch(meterRegistry, "cache.gets.latency").functionTimer();
        assertThat(getsLatency.count()).isEqualTo(localMapStats.getGetOperationCount());
        assertThat(getsLatency.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(localMapStats.getTotalGetLatency());

        FunctionTimer putsLatency = fetch(meterRegistry, "cache.puts.latency").functionTimer();
        assertThat(putsLatency.count()).isEqualTo(localMapStats.getPutOperationCount());
        assertThat(putsLatency.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(localMapStats.getTotalPutLatency());

        FunctionTimer removeLatency = fetch(meterRegistry, "cache.removals.latency").functionTimer();
        assertThat(removeLatency.count()).isEqualTo(localMapStats.getRemoveOperationCount());
        assertThat(removeLatency.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(localMapStats.getTotalPutLatency());
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
        NearCacheStats nearCacheStats = mock(NearCacheStatsImpl.class);
        Random random = new Random();
        when(nearCacheStats.getHits()).thenReturn(random.nextLong());
        when(nearCacheStats.getMisses()).thenReturn(random.nextLong());
        when(nearCacheStats.getPersistenceCount()).thenReturn(random.nextLong());
        when(nearCacheStats.getEvictions()).thenReturn(random.nextLong());
        ((LocalMapStatsImpl) cache.getLocalMapStats()).setNearCacheStats(nearCacheStats);
    }

    @AfterAll
    static void cleanup() {
        Hazelcast.shutdownAll();
    }

    @Override
    protected Tags getTags() {
        return expectedTag;
    }

}
