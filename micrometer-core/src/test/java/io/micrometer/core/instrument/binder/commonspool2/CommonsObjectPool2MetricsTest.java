/**
 * Copyright 2020 Pivotal Software, Inc.
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

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Chao Chang
 */
class CommonsObjectPool2MetricsTest {
    private int genericObjectPoolCount = 0;

    private Tags tags = Tags.of("app", "myapp", "version", "1");
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final CommonsObjectPool2Metrics commonsObjectPool2Metrics = new CommonsObjectPool2Metrics(tags);

    @Test
    void verifyMetricsWithExpectedTags() {
        try (GenericObjectPool<Object> p = createGenericObjectPool()) {
            MeterRegistry registry = new SimpleMeterRegistry();
            commonsObjectPool2Metrics.bindTo(registry);

            registry.get("commons.pool2.num.idle").tags(tags).gauge();
            registry.get("commons.pool2.num.waiters").tags(tags).gauge();

            Arrays.asList(
                    "commons.pool2.created",
                    "commons.pool2.borrowed",
                    "commons.pool2.returned",
                    "commons.pool2.destroyed",
                    "commons.pool2.destroyed.by.evictor",
                    "commons.pool2.destroyed.by.borrow.validation"
            ).forEach(name -> registry.get(name).tags(tags).functionCounter());

            Arrays.asList(
                    "commons.pool2.max.borrow.wait",
                    "commons.pool2.mean.active",
                    "commons.pool2.mean.idle",
                    "commons.pool2.mean.borrow.wait"
            ).forEach(name -> registry.get(name).tags(tags).timeGauge());
        }
    }

    @Test
    void verifyGenericKeyedObjectPoolMetricsWithExpectedTags() {
        try (GenericKeyedObjectPool<Object, Object> p = createGenericKeyedObjectPool()) {
            Tags tagsToMatch = tags.and("type", "GenericKeyedObjectPool");
            commonsObjectPool2Metrics.bindTo(registry);

            Arrays.asList(
                    "commons.pool2.num.idle",
                    "commons.pool2.num.waiters"
            ).forEach(name -> registry.get(name).tags(tagsToMatch).gauge());

            Arrays.asList(
                    "commons.pool2.created",
                    "commons.pool2.borrowed",
                    "commons.pool2.returned",
                    "commons.pool2.destroyed",
                    "commons.pool2.destroyed.by.evictor",
                    "commons.pool2.destroyed.by.borrow.validation"
            ).forEach(name -> registry.get(name).tags(tagsToMatch).functionCounter());

            Arrays.asList(
                    "commons.pool2.max.borrow.wait",
                    "commons.pool2.mean.active",
                    "commons.pool2.mean.idle",
                    "commons.pool2.mean.borrow.wait"
            ).forEach(name -> registry.get(name).tags(tagsToMatch).timeGauge());
        }
    }

    @Test
    void verifyIdleAndActiveInstancesAreReported() throws Exception {
        commonsObjectPool2Metrics.bindTo(registry);
        CountDownLatch latch = new CountDownLatch(1);
        registry.config().onMeterAdded(m -> {
            if (m.getId().getName().contains("commons.pool2"))
                latch.countDown();
        });

        try (GenericObjectPool<Object> genericObjectPool = createGenericObjectPool()) {
            latch.await(10, TimeUnit.SECONDS);
            final Object o = genericObjectPool.borrowObject(10_000L);

            assertThat(registry.get("commons.pool2.num.active").gauge().value()).isEqualTo(1.0);
            assertThat(registry.get("commons.pool2.num.idle").gauge().value()).isEqualTo(0.0);

            genericObjectPool.returnObject(o);

            assertThat(registry.get("commons.pool2.num.active").gauge().value()).isEqualTo(0.0);
            assertThat(registry.get("commons.pool2.num.idle").gauge().value()).isEqualTo(1.0);
        }
    }

    @Test
    void metricsReportedPerMultiplePools() {
        try (final GenericObjectPool<Object> p1 = createGenericObjectPool();
            final GenericObjectPool<Object> p2 = createGenericObjectPool();
            final GenericObjectPool<Object> p3 = createGenericObjectPool()) {

            MeterRegistry registry = new SimpleMeterRegistry();
            commonsObjectPool2Metrics.bindTo(registry);

            registry.get("commons.pool2.num.waiters").tag("name", "pool" + genericObjectPoolCount).gauge();
            registry.get("commons.pool2.num.waiters").tag("name", "pool" + (genericObjectPoolCount - 1)).gauge();
        }
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

        try (GenericObjectPool<Object> p = createGenericObjectPool()) {
            latch.await(10, TimeUnit.SECONDS);
        }
    }

    private GenericObjectPool<Object> createGenericObjectPool() {
        genericObjectPoolCount++;
        GenericObjectPoolConfig<Object> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(10);

        return new GenericObjectPool<>(new BasePooledObjectFactory<Object>() {
            @Override
            public Object create() {
                return new Object();
            }

            @Override
            public PooledObject<Object> wrap(Object testObject) {
                return new DefaultPooledObject<>(testObject);
            }
        }, config);
    }

    private GenericKeyedObjectPool<Object, Object> createGenericKeyedObjectPool() {
        return new GenericKeyedObjectPool<>(new BaseKeyedPooledObjectFactory<Object, Object>() {
            @Override
            public Object create(Object key) {
                return key;
            }

            @Override
            public PooledObject<Object> wrap(Object value) {
                return new DefaultPooledObject<>(value);
            }
        });
    }
}
