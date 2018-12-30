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

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EhCache2Metrics}.
 *
 * @author Oleksii Bondar
 */
class EhCache2MetricsTest extends AbstractCacheMetricsTest {

    private static CacheManager cacheManager;
    private static Cache cache;

    @Test
    void reportMetrics() {
        Tags expectedTag = Tags.of("app", "test");
        EhCache2Metrics metrics = new EhCache2Metrics(cache, expectedTag);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        metrics.bindTo(meterRegistry);

        verifyCommonCacheMetrics(meterRegistry);

        meterRegistry.get("cache.remoteSize").tags(expectedTag).gauge();
        meterRegistry.get("cache.removals").tags(expectedTag).functionCounter();
        meterRegistry.get("cache.puts.added").tags(expectedTag).functionCounter();
        meterRegistry.get("cache.puts.added").tags(expectedTag).functionCounter();
        meterRegistry.get("cache.local.offheap.size").tags(expectedTag).gauge();
        meterRegistry.get("cache.local.heap.size").tags(expectedTag).gauge();
        meterRegistry.get("cache.local.disk.size").tags(expectedTag).gauge();

        // miss metrics
        meterRegistry.get("cache.misses").tags(expectedTag).tag("reason", "expired").functionCounter();
        meterRegistry.get("cache.misses").tags(expectedTag).tag("reason", "notFound").functionCounter();

        // commit transaction metrics
        meterRegistry.get("cache.xa.commits").tags(expectedTag).tag("result", "readOnly").functionCounter();
        meterRegistry.get("cache.xa.commits").tags(expectedTag).tag("result", "exception").functionCounter();
        meterRegistry.get("cache.xa.commits").tags(expectedTag).tag("result", "committed").functionCounter();

        // rollback transaction metrics
        meterRegistry.get("cache.xa.rollbacks").tags(expectedTag).tag("result", "exception").functionCounter();
        meterRegistry.get("cache.xa.rollbacks").tags(expectedTag).tag("result", "success").functionCounter();

        // recovery transaction metrics
        meterRegistry.get("cache.xa.recoveries").tags(expectedTag).tag("result", "nothing").functionCounter();
        meterRegistry.get("cache.xa.recoveries").tags(expectedTag).tag("result", "success").functionCounter();

    }

    @BeforeAll
    static void setup() {
        cacheManager = CacheManager.newInstance();
        cacheManager.addCache("testCache");
        cache = cacheManager.getCache("testCache");
    }

    @AfterAll
    static void cleanup() {
        cacheManager.removeAllCaches();
    }
}
