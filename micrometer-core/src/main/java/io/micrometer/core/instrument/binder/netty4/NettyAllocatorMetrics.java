/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.netty4;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.netty.buffer.ByteBufAllocatorMetric;
import io.netty.buffer.ByteBufAllocatorMetricProvider;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocatorMetric;

/**
 * {@link MeterBinder} for Netty memory allocators.
 *
 * @author Brian Clozel
 * @since 1.11.0
 * @see NettyMeters
 */
public class NettyAllocatorMetrics implements MeterBinder {

    private final ByteBufAllocatorMetricProvider allocator;

    /**
     * Create a binder instance for the given allocator.
     * @param allocator the {@code ByteBuf} allocator to instrument
     */
    public NettyAllocatorMetrics(ByteBufAllocatorMetricProvider allocator) {
        this.allocator = allocator;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        int allocatorId = this.allocator.hashCode();

        ByteBufAllocatorMetric allocatorMetric = this.allocator.metric();
        Tags tags = Tags.of(NettyMeters.AllocatorKeyNames.ID.asString(), String.valueOf(allocatorId),
                NettyMeters.AllocatorKeyNames.ALLOCATOR_TYPE.asString(), this.allocator.getClass().getSimpleName());

        Gauge
            .builder(NettyMeters.ALLOCATOR_MEMORY_USED.getName(), allocatorMetric,
                    ByteBufAllocatorMetric::usedHeapMemory)
            .tags(tags.and(NettyMeters.AllocatorMemoryKeyNames.MEMORY_TYPE.asString(), "heap"))
            .register(registry);

        Gauge
            .builder(NettyMeters.ALLOCATOR_MEMORY_USED.getName(), allocatorMetric,
                    ByteBufAllocatorMetric::usedDirectMemory)
            .tags(tags.and(NettyMeters.AllocatorMemoryKeyNames.MEMORY_TYPE.asString(), "direct"))
            .register(registry);

        if (this.allocator instanceof PooledByteBufAllocator) {
            PooledByteBufAllocator pooledByteBufAllocator = (PooledByteBufAllocator) this.allocator;
            PooledByteBufAllocatorMetric pooledAllocatorMetric = pooledByteBufAllocator.metric();

            Gauge
                .builder(NettyMeters.ALLOCATOR_MEMORY_PINNED.getName(), pooledByteBufAllocator,
                        PooledByteBufAllocator::pinnedHeapMemory)
                .tags(tags.and(NettyMeters.AllocatorMemoryKeyNames.MEMORY_TYPE.asString(), "heap"))
                .register(registry);

            Gauge
                .builder(NettyMeters.ALLOCATOR_MEMORY_PINNED.getName(), pooledByteBufAllocator,
                        PooledByteBufAllocator::pinnedDirectMemory)
                .tags(tags.and(NettyMeters.AllocatorMemoryKeyNames.MEMORY_TYPE.asString(), "direct"))
                .register(registry);

            Gauge
                .builder(NettyMeters.ALLOCATOR_POOLED_ARENAS.getName(), pooledAllocatorMetric,
                        PooledByteBufAllocatorMetric::numHeapArenas)
                .tags(tags.and(NettyMeters.AllocatorMemoryKeyNames.MEMORY_TYPE.asString(), "heap"))
                .register(registry);
            Gauge
                .builder(NettyMeters.ALLOCATOR_POOLED_ARENAS.getName(), pooledAllocatorMetric,
                        PooledByteBufAllocatorMetric::numDirectArenas)
                .tags(tags.and(NettyMeters.AllocatorMemoryKeyNames.MEMORY_TYPE.asString(), "direct"))
                .register(registry);

            Gauge
                .builder(NettyMeters.ALLOCATOR_POOLED_CACHE_SIZE.getName(), pooledAllocatorMetric,
                        PooledByteBufAllocatorMetric::normalCacheSize)
                .tags(tags.and(NettyMeters.AllocatorPooledCacheKeyNames.CACHE_TYPE.asString(), "normal"))
                .register(registry);
            Gauge
                .builder(NettyMeters.ALLOCATOR_POOLED_CACHE_SIZE.getName(), pooledAllocatorMetric,
                        PooledByteBufAllocatorMetric::smallCacheSize)
                .tags(tags.and(NettyMeters.AllocatorPooledCacheKeyNames.CACHE_TYPE.asString(), "small"))
                .register(registry);

            Gauge
                .builder(NettyMeters.ALLOCATOR_POOLED_THREADLOCAL_CACHES.getName(), pooledAllocatorMetric,
                        PooledByteBufAllocatorMetric::numThreadLocalCaches)
                .tags(tags)
                .register(registry);

            Gauge
                .builder(NettyMeters.ALLOCATOR_POOLED_CHUNK_SIZE.getName(), pooledAllocatorMetric,
                        PooledByteBufAllocatorMetric::chunkSize)
                .tags(tags)
                .register(registry);
        }
    }

}
