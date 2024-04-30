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

import com.hazelcast.core.Hazelcast;
import com.hazelcast.internal.monitor.impl.LocalMapStatsImpl;
import com.hazelcast.internal.monitor.impl.NearCacheStatsImpl;
import com.hazelcast.map.IMap;
import com.hazelcast.map.LocalMapStats;
import com.hazelcast.nearcache.NearCacheStats;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HazelcastCacheMetrics}.
 *
 * @author Oleksii Bondar
 */
class HazelcastCacheMetricsTest extends AbstractCacheMetricsTest {

    // tag::setup[]
    static IMap<String, String> cache;

    HazelcastCacheMetrics metrics = new HazelcastCacheMetrics(cache, expectedTag);

    // end::setup[]

    @Test
    void reportMetrics() {
        // tag::register[]
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics.bindTo(meterRegistry);
        // end::register[]

        verifyCommonCacheMetrics(meterRegistry, metrics);

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

    @Test
    void constructInstanceViaStaticMethodMonitor() {
        // tag::monitor[]
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        HazelcastCacheMetrics.monitor(meterRegistry, cache, expectedTag);
        // end::monitor[]

        meterRegistry.get("cache.partition.gets").tags(expectedTag).functionCounter();
    }

    @Test
    void doNotReportEvictionCountSinceNotImplemented() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        HazelcastCacheMetrics.monitor(meterRegistry, cache, expectedTag);

        assertThat(meterRegistry.find("cache.evictions").functionCounter()).isNull();
    }

    @Test
    void doNotReportMissCountSinceNotImplemented() {
        MeterRegistry registry = new SimpleMeterRegistry();
        HazelcastCacheMetrics.monitor(registry, cache, expectedTag);

        assertThat(registry.find("cache.gets").tags(Tags.of("result", "miss")).functionCounter()).isNull();
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
        assertThat(metrics.putCount()).isEqualTo(
                cache.getLocalMapStats().getPutOperationCount() + cache.getLocalMapStats().getSetOperationCount());
    }

    @Test
    void nonIMapCacheFails() {
        assertThatExceptionOfType(RuntimeException.class)
            .isThrownBy(() -> new HazelcastCacheMetrics(new HashMap<String, String>(), Tags.empty()));
    }

    @BeforeAll
    static void setup() {
        cache = Hazelcast.newHazelcastInstance().getMap("mycache");
        NearCacheStats nearCacheStats = mock(NearCacheStatsImpl.class);
        // generate non-negative random value to address false-positives
        int valueBound = 100000;
        Random random = new Random();
        when(nearCacheStats.getMisses()).thenReturn((long) random.nextInt(valueBound));
        when(nearCacheStats.getPersistenceCount()).thenReturn((long) random.nextInt(valueBound));
        when(nearCacheStats.getEvictions()).thenReturn((long) random.nextInt(valueBound));

        LocalMapStatsImpl localMapStats = (LocalMapStatsImpl) cache.getLocalMapStats();
        localMapStats.setNearCacheStats(nearCacheStats);

        localMapStats.setBackupEntryCount(random.nextInt(valueBound));
        localMapStats.setBackupEntryMemoryCost(random.nextInt(valueBound));
        localMapStats.setOwnedEntryCount(random.nextInt(valueBound));
        localMapStats.setOwnedEntryMemoryCost(random.nextInt(valueBound));
        localMapStats.incrementGetLatencyNanos(random.nextInt(valueBound));
        localMapStats.incrementPutLatencyNanos(random.nextInt(valueBound));
        localMapStats.incrementSetLatencyNanos(random.nextInt(valueBound));
        localMapStats.incrementRemoveLatencyNanos(random.nextInt(valueBound));
    }

    @AfterAll
    static void cleanup() {
        Hazelcast.shutdownAll();
    }

}
