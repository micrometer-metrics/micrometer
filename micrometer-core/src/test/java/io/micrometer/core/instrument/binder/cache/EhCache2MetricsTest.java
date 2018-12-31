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

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Random;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.statistics.StatisticsGateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EhCache2Metrics}.
 *
 * @author Oleksii Bondar
 */
class EhCache2MetricsTest extends AbstractCacheMetricsTest {

    private static CacheManager cacheManager;
    private static Cache cache;

    private Tags expectedTag = Tags.of("app", "test");
    private EhCache2Metrics metrics = new EhCache2Metrics(cache, expectedTag);

    @Test
    void reportMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        metrics.bindTo(registry);

        verifyCommonCacheMetrics(registry);

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
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EhCache2Metrics.monitor(meterRegistry, cache, expectedTag);

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
        cache = cacheManager.getCache("testCache");
        StatisticsGateway stats = mock(StatisticsGateway.class);
        // randomize stats to address false-positive checks
        Random random = new Random();
        when(stats.getSize()).thenReturn(random.nextLong());
        when(stats.cacheEvictedCount()).thenReturn(random.nextLong());
        when(stats.cacheHitCount()).thenReturn(random.nextLong());
        when(stats.cacheMissCount()).thenReturn(random.nextLong());
        when(stats.cachePutCount()).thenReturn(random.nextLong());
        when(stats.getRemoteSize()).thenReturn(random.nextLong());
        when(stats.cacheRemoveCount()).thenReturn(random.nextLong());
        when(stats.cachePutAddedCount()).thenReturn(random.nextLong());
        when(stats.cachePutUpdatedCount()).thenReturn(random.nextLong());
        when(stats.getLocalOffHeapSizeInBytes()).thenReturn(random.nextLong());
        when(stats.getLocalHeapSizeInBytes()).thenReturn(random.nextLong());
        when(stats.getLocalDiskSizeInBytes()).thenReturn(random.nextLong());
        when(stats.cacheMissExpiredCount()).thenReturn(random.nextLong());
        when(stats.cacheMissNotFoundCount()).thenReturn(random.nextLong());
        when(stats.xaCommitCommittedCount()).thenReturn(random.nextLong());
        when(stats.xaCommitExceptionCount()).thenReturn(random.nextLong());
        when(stats.xaCommitReadOnlyCount()).thenReturn(random.nextLong());
        when(stats.xaRollbackExceptionCount()).thenReturn(random.nextLong());
        when(stats.xaRollbackSuccessCount()).thenReturn(random.nextLong());
        when(stats.xaRecoveryRecoveredCount()).thenReturn(random.nextLong());
        when(stats.xaRecoveryNothingCount()).thenReturn(random.nextLong());
        ReflectionTestUtils.setField(cache, "statistics", stats);
    }

    @AfterAll
    static void cleanup() {
        cacheManager.removeAllCaches();
    }

    @Override
    protected Tags getTags() {
        return expectedTag;
    }
}
