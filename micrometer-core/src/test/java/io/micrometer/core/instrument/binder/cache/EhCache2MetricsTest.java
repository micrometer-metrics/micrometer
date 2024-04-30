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

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.statistics.StatisticsGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link EhCache2Metrics}.
 *
 * @author Oleksii Bondar
 */
class EhCache2MetricsTest extends AbstractCacheMetricsTest {

    private static CacheManager cacheManager;

    // tag::setup[]
    static Cache cache;

    EhCache2Metrics metrics = new EhCache2Metrics(cache, expectedTag);

    // end::setup[]

    @Test
    void reportMetrics() {
        // tag::register[]
        MeterRegistry registry = new SimpleMeterRegistry();
        metrics.bindTo(registry);
        // end::register[]

        verifyCommonCacheMetrics(registry, metrics);

        StatisticsGateway stats = cache.getStatistics();

        Gauge remoteSize = fetch(registry, "cache.remoteSize").gauge();
        assertThat(remoteSize.value()).isEqualTo(stats.getRemoteSize());

        FunctionCounter cacheRemovals = fetch(registry, "cache.removals").functionCounter();
        assertThat(cacheRemovals.count()).isEqualTo(stats.cacheRemoveCount());

        String cacheAdded = "cache.puts.added";
        FunctionCounter putsAdded = fetch(registry, cacheAdded, Tags.of("result", "added")).functionCounter();
        assertThat(putsAdded.count()).isEqualTo(stats.cachePutAddedCount());

        FunctionCounter putsUpdated = fetch(registry, cacheAdded, Tags.of("result", "updated")).functionCounter();
        assertThat(putsUpdated.count()).isEqualTo(stats.cachePutUpdatedCount());

        Gauge offHeapSize = fetch(registry, "cache.local.offheap.size").gauge();
        assertThat(offHeapSize.value()).isEqualTo(stats.getLocalOffHeapSizeInBytes());

        Gauge heapSize = fetch(registry, "cache.local.heap.size").gauge();
        assertThat(heapSize.value()).isEqualTo(stats.getLocalHeapSizeInBytes());

        Gauge diskSize = fetch(registry, "cache.local.disk.size").gauge();
        assertThat(diskSize.value()).isEqualTo(stats.getLocalDiskSizeInBytes());

        // miss metrics
        String misses = "cache.misses";
        FunctionCounter expiredMisses = fetch(registry, misses, Tags.of("reason", "expired")).functionCounter();
        assertThat(expiredMisses.count()).isEqualTo(stats.cacheMissExpiredCount());

        FunctionCounter notFoundMisses = fetch(registry, misses, Tags.of("reason", "notFound")).functionCounter();
        assertThat(notFoundMisses.count()).isEqualTo(stats.cacheMissNotFoundCount());

        // commit transaction metrics
        String xaCommits = "cache.xa.commits";
        FunctionCounter readOnlyCommits = fetch(registry, xaCommits, Tags.of("result", "readOnly")).functionCounter();
        assertThat(readOnlyCommits.count()).isEqualTo(stats.xaCommitReadOnlyCount());

        FunctionCounter exceptionCommits = fetch(registry, xaCommits, Tags.of("result", "exception")).functionCounter();
        assertThat(exceptionCommits.count()).isEqualTo(stats.xaCommitExceptionCount());

        FunctionCounter committedCommits = fetch(registry, xaCommits, Tags.of("result", "committed")).functionCounter();
        assertThat(committedCommits.count()).isEqualTo(stats.xaCommitCommittedCount());

        // rollback transaction metrics
        String xaRollbacks = "cache.xa.rollbacks";
        FunctionCounter exceptionRollback = fetch(registry, xaRollbacks, Tags.of("result", "exception"))
            .functionCounter();
        assertThat(exceptionRollback.count()).isEqualTo(stats.xaRollbackExceptionCount());

        FunctionCounter successRollback = fetch(registry, xaRollbacks, Tags.of("result", "success")).functionCounter();
        assertThat(successRollback.count()).isEqualTo(stats.xaRollbackSuccessCount());

        // recovery transaction metrics
        String xaRecoveries = "cache.xa.recoveries";
        FunctionCounter nothingRecovered = fetch(registry, xaRecoveries, Tags.of("result", "nothing"))
            .functionCounter();
        assertThat(nothingRecovered.count()).isEqualTo(stats.xaRecoveryNothingCount());

        FunctionCounter successRecoveries = fetch(registry, xaRecoveries, Tags.of("result", "success"))
            .functionCounter();
        assertThat(successRecoveries.count()).isEqualTo(stats.xaRecoveryRecoveredCount());
    }

    @Test
    void constructInstanceViaStaticMethodMonitor() {
        // tag::monitor[]
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        EhCache2Metrics.monitor(meterRegistry, cache, expectedTag);
        // end::monitor[]

        meterRegistry.get("cache.remoteSize").tags(expectedTag).gauge();
    }

    @Test
    void returnCacheSize() {
        StatisticsGateway stats = cache.getStatistics();
        assertThat(metrics.size()).isEqualTo(stats.getSize());
    }

    @Test
    void returnEvictionCount() {
        StatisticsGateway stats = cache.getStatistics();
        assertThat(metrics.evictionCount()).isEqualTo(stats.cacheEvictedCount());
    }

    @Test
    void returnHitCount() {
        StatisticsGateway stats = cache.getStatistics();
        assertThat(metrics.hitCount()).isEqualTo(stats.cacheHitCount());
    }

    @Test
    void returnMissCount() {
        StatisticsGateway stats = cache.getStatistics();
        assertThat(metrics.missCount()).isEqualTo(stats.cacheMissCount());
    }

    @Test
    void returnPutCount() {
        StatisticsGateway stats = cache.getStatistics();
        assertThat(metrics.putCount()).isEqualTo(stats.cachePutCount());
    }

    @BeforeAll
    static void setup() {
        cacheManager = CacheManager.newInstance();
        cacheManager.addCache("testCache");
        cache = spy(cacheManager.getCache("testCache"));
        StatisticsGateway stats = mock(StatisticsGateway.class);
        // generate non-negative random value to address false-positives
        int valueBound = 100000;
        Random random = new Random();
        when(stats.getSize()).thenReturn((long) random.nextInt(valueBound));
        when(stats.cacheEvictedCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.cacheHitCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.cacheMissCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.cachePutCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.getRemoteSize()).thenReturn((long) random.nextInt(valueBound));
        when(stats.cacheRemoveCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.cachePutAddedCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.cachePutUpdatedCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.getLocalOffHeapSizeInBytes()).thenReturn((long) random.nextInt(valueBound));
        when(stats.getLocalHeapSizeInBytes()).thenReturn((long) random.nextInt(valueBound));
        when(stats.getLocalDiskSizeInBytes()).thenReturn((long) random.nextInt(valueBound));
        when(stats.cacheMissExpiredCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.cacheMissNotFoundCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.xaCommitCommittedCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.xaCommitExceptionCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.xaCommitReadOnlyCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.xaRollbackExceptionCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.xaRollbackSuccessCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.xaRecoveryRecoveredCount()).thenReturn((long) random.nextInt(valueBound));
        when(stats.xaRecoveryNothingCount()).thenReturn((long) random.nextInt(valueBound));
        when(cache.getStatistics()).thenReturn(stats);
    }

    @AfterAll
    static void cleanup() {
        cacheManager.removeAllCaches();
    }

}
