/**
 * Copyright 2018 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.commonspool2;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class CommonsObjectPool2MetricsTest {
    private static int genericObjectPoolCount = 0;

    private Tags tags = Tags.of("app", "myapp", "version", "1");
    private CommonsObjectPool2Metrics commonsObjectPool2Metrics = new CommonsObjectPool2Metrics(tags);

    @Test
    void verifyMetricsWithExpectedTags() {
        GenericObjectPool objectPool = createGenericObjectPool();
        MeterRegistry registry = new SimpleMeterRegistry();
        commonsObjectPool2Metrics.bindTo(registry);
        String[] gaugeNames = new String[]{"commons.pool2.numIdle",
                "commons.pool2.numWaiters"};
        for (String name : gaugeNames) {
            registry.get(name).tags(tags).gauge();
        }

        String[] functionCounterNames = new String[]{"commons.pool2.createdCount", "commons.pool2.borrowedCount",
                "commons.pool2.returnedCount", "commons.pool2.destroyedCount", "commons.pool2.destroyedByEvictorCount", "commons.pool2.destroyedByBorrowValidationCount",};
        for (String name : functionCounterNames) {
            registry.get(name).tags(tags).functionCounter();
        }

        String[] timeGaugeNames = new String[]{"commons.pool2.maxBorrowWaitTime", "commons.pool2.meanActiveTime", "commons.pool2.meanIdleTime", "commons.pool2.meanBorrowWaitTime"};
        for (String name : timeGaugeNames) {
            registry.get(name).tags(tags).timeGauge();
        }

    }

    @Test
    void verifyGenericKeyedObjectPoolMetricsWithExpectedTags() throws Exception {
        GenericKeyedObjectPool objectPool = createGenericKeyedObjectPool();
        Tags tagsToMatch = tags.and("type", "GenericKeyedObjectPool");
        MeterRegistry registry = new SimpleMeterRegistry();
        commonsObjectPool2Metrics.bindTo(registry);
        String[] gaugeNames = new String[]{"commons.pool2.numIdle",
                "commons.pool2.numWaiters"};
        for (String name : gaugeNames) {
            registry.get(name).tags(tagsToMatch).gauge();
        }

        String[] functionCounterNames = new String[]{"commons.pool2.createdCount", "commons.pool2.borrowedCount",
                "commons.pool2.returnedCount", "commons.pool2.destroyedCount", "commons.pool2.destroyedByEvictorCount", "commons.pool2.destroyedByBorrowValidationCount",};
        for (String name : functionCounterNames) {
            registry.get(name).tags(tagsToMatch).functionCounter();
        }

        String[] timeGaugeNames = new String[]{"commons.pool2.maxBorrowWaitTime", "commons.pool2.meanActiveTime", "commons.pool2.meanIdleTime", "commons.pool2.meanBorrowWaitTime"};
        for (String name : timeGaugeNames) {
            registry.get(name).tags(tagsToMatch).timeGauge();
        }

    }

    @Test
    void metricsReportedPerMultiplePools() {
        GenericObjectPool objectPool1 = createGenericObjectPool();
        GenericObjectPool objectPool2 = createGenericObjectPool();
        GenericObjectPool objectPool3 = createGenericObjectPool();
        MeterRegistry registry = new SimpleMeterRegistry();
        commonsObjectPool2Metrics.bindTo(registry);

        // fetch metrics
        registry.get("commons.pool2.numWaiters").tag("name", "pool" + genericObjectPoolCount).gauge();
        registry.get("commons.pool2.numWaiters").tag("name", "pool" + (genericObjectPoolCount - 1)).gauge();
    }

    @Test
    void newPoolsAreDiscoveredByListener() throws InterruptedException {
        MeterRegistry registry = new SimpleMeterRegistry();
        commonsObjectPool2Metrics.bindTo(registry);

        CountDownLatch latch = new CountDownLatch(1);
        registry.config().onMeterAdded(m -> {
            if (m.getId().getName().contains("commons.pool2"))
                latch.countDown();
        });

        GenericObjectPool objectPool = createGenericObjectPool();
        latch.await(10, TimeUnit.SECONDS);
    }

    private GenericObjectPool createGenericObjectPool() {
        genericObjectPoolCount++;
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxTotal(10);

        GenericObjectPool objectPool = new GenericObjectPool<>(new BasePooledObjectFactory<Object>() {
            @Override
            public Object create() throws Exception {
                return new Object();
            }

            @Override
            public PooledObject<Object> wrap(Object testObject) {
                return new DefaultPooledObject<>(testObject);
            }
        }, config);
        return objectPool;
    }

    private GenericKeyedObjectPool createGenericKeyedObjectPool() {
        GenericKeyedObjectPool pool = new GenericKeyedObjectPool(new BaseKeyedPooledObjectFactory() {
            @Override
            public Object create(Object key) throws Exception {
                return key;
            }

            @Override
            public PooledObject wrap(Object value) {
                return new DefaultPooledObject(value);
            }
        });
        return pool;
    }

}
