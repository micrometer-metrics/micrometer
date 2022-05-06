/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.samples.hazelcast3;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.monitor.LocalMapStats;
import com.hazelcast.monitor.NearCacheStats;
import com.hazelcast.monitor.impl.LocalMapStatsImpl;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Copied from micrometer-core test HazelcastCacheMetricsTest here to ensure cache metrics
 * work with Hazelcast 3.
 */
class Hazelcast3CacheMetricsTest {

    HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance();

    IMap<String, String> cache = hazelcastInstance.getMap("mycache");

    HazelcastCacheMetrics metrics = new HazelcastCacheMetrics(cache, Tags.empty());

    LocalMapStats localMapStats = cache.getLocalMapStats();

    NearCacheStats nearCacheStats = mock(NearCacheStats.class);

    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setup() {
        LocalMapStatsImpl localMapStatsImpl = (LocalMapStatsImpl) this.localMapStats;
        localMapStatsImpl.setNearCacheStats(nearCacheStats);

        metrics.bindTo(meterRegistry);
        cache.put("hello", "world");
        cache.get("hello");
        cache.get("hi");
        cache.get("hello");
    }

    @AfterEach
    void cleanUp() {
        hazelcastInstance.shutdown();
    }

    @Test
    void generalMetrics() {
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
    }

    @Test
    void nearCacheMetrics() {
        when(nearCacheStats.getHits()).thenReturn(1L);
        when(nearCacheStats.getMisses()).thenReturn(2L);
        when(nearCacheStats.getPersistenceCount()).thenReturn(3L);
        when(nearCacheStats.getEvictions()).thenReturn(4L);

        Gauge hitCacheRequests = fetch(meterRegistry, "cache.near.requests", Tags.of("result", "hit")).gauge();
        assertThat(hitCacheRequests.value()).isEqualTo(nearCacheStats.getHits());

        Gauge missCacheRequests = fetch(meterRegistry, "cache.near.requests", Tags.of("result", "miss")).gauge();
        assertThat(missCacheRequests.value()).isEqualTo(nearCacheStats.getMisses());

        Gauge nearPersistences = fetch(meterRegistry, "cache.near.persistences").gauge();
        assertThat(nearPersistences.value()).isEqualTo(nearCacheStats.getPersistenceCount());

        Gauge nearEvictions = fetch(meterRegistry, "cache.near.evictions").gauge();
        assertThat(nearEvictions.value()).isEqualTo(nearCacheStats.getEvictions());
    }

    @Test
    void timingsMetrics() {
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;
        FunctionTimer getsLatency = fetch(meterRegistry, "cache.gets.latency").functionTimer();
        assertThat(getsLatency.count()).isEqualTo(localMapStats.getGetOperationCount());
        assertThat(getsLatency.totalTime(timeUnit)).isEqualTo(localMapStats.getTotalGetLatency());

        FunctionTimer putsLatency = fetch(meterRegistry, "cache.puts.latency").functionTimer();
        assertThat(putsLatency.count()).isEqualTo(localMapStats.getPutOperationCount());
        assertThat(putsLatency.totalTime(timeUnit)).isEqualTo(localMapStats.getTotalPutLatency());

        FunctionTimer removeLatency = fetch(meterRegistry, "cache.removals.latency").functionTimer();
        assertThat(removeLatency.count()).isEqualTo(localMapStats.getRemoveOperationCount());
        assertThat(removeLatency.totalTime(timeUnit)).isEqualTo(localMapStats.getTotalRemoveLatency());
    }

    private RequiredSearch fetch(MeterRegistry meterRegistry, String name) {
        return fetch(meterRegistry, name, Tags.empty());
    }

    private RequiredSearch fetch(MeterRegistry meterRegistry, String name, Iterable<Tag> tags) {
        return meterRegistry.get(name).tags(tags);
    }

}
