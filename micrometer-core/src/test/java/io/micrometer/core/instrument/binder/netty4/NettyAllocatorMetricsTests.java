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

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NettyAllocatorMetrics}.
 *
 * @author Brian Clozel
 */
class NettyAllocatorMetricsTests {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void shouldHaveHeapMemoryUsedMetricsForUnpooledAllocator() {
        UnpooledByteBufAllocator unpooledByteBufAllocator = new UnpooledByteBufAllocator(false);
        NettyAllocatorMetrics binder = new NettyAllocatorMetrics(unpooledByteBufAllocator);
        binder.bindTo(this.registry);
        ByteBuf buffer = unpooledByteBufAllocator.buffer();
        Tags tags = Tags.of("id", String.valueOf(unpooledByteBufAllocator.hashCode()), "allocator.type",
                "UnpooledByteBufAllocator", "memory.type", "heap");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_MEMORY_USED.getName()).tags(tags).gauge().value())
            .isPositive();
        buffer.release();
    }

    @Test
    void shouldHaveDirectMemoryUsedMetricsForUnpooledAllocator() {
        UnpooledByteBufAllocator unpooledByteBufAllocator = new UnpooledByteBufAllocator(false);
        NettyAllocatorMetrics binder = new NettyAllocatorMetrics(unpooledByteBufAllocator);
        binder.bindTo(this.registry);
        ByteBuf buffer = unpooledByteBufAllocator.directBuffer();
        Tags tags = Tags.of("id", String.valueOf(unpooledByteBufAllocator.hashCode()), "allocator.type",
                "UnpooledByteBufAllocator", "memory.type", "direct");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_MEMORY_USED.getName()).tags(tags).gauge().value())
            .isPositive();
        buffer.release();
    }

    @Test
    void shouldHaveHeapMemoryUsedMetricsForPooledAllocator() {
        PooledByteBufAllocator pooledByteBufAllocator = new PooledByteBufAllocator();
        NettyAllocatorMetrics binder = new NettyAllocatorMetrics(pooledByteBufAllocator);
        binder.bindTo(this.registry);
        ByteBuf buffer = pooledByteBufAllocator.buffer();
        Tags tags = Tags.of("id", String.valueOf(pooledByteBufAllocator.hashCode()), "allocator.type",
                "PooledByteBufAllocator", "memory.type", "heap");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_MEMORY_USED.getName()).tags(tags).gauge().value())
            .isPositive();
        buffer.release();
    }

    @Test
    void shouldHaveDirectMemoryUsedMetricsForPooledAllocator() {
        PooledByteBufAllocator pooledByteBufAllocator = new PooledByteBufAllocator();
        NettyAllocatorMetrics binder = new NettyAllocatorMetrics(pooledByteBufAllocator);
        binder.bindTo(this.registry);
        ByteBuf buffer = pooledByteBufAllocator.directBuffer();
        Tags tags = Tags.of("id", String.valueOf(pooledByteBufAllocator.hashCode()), "allocator.type",
                "PooledByteBufAllocator", "memory.type", "direct");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_MEMORY_USED.getName()).tags(tags).gauge().value())
            .isPositive();
        buffer.release();
    }

    @Test
    void shouldHaveHeapMemoryPinnedMetricsForPooledAllocator() {
        PooledByteBufAllocator pooledByteBufAllocator = new PooledByteBufAllocator();
        NettyAllocatorMetrics binder = new NettyAllocatorMetrics(pooledByteBufAllocator);
        binder.bindTo(this.registry);
        ByteBuf buffer = pooledByteBufAllocator.buffer();
        Tags tags = Tags.of("id", String.valueOf(pooledByteBufAllocator.hashCode()), "allocator.type",
                "PooledByteBufAllocator", "memory.type", "heap");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_MEMORY_PINNED.getName()).tags(tags).gauge().value())
            .isPositive();
        buffer.release();
    }

    @Test
    void shouldHaveDirectMemoryPinnedMetricsForPooledAllocator() {
        PooledByteBufAllocator pooledByteBufAllocator = new PooledByteBufAllocator();
        NettyAllocatorMetrics binder = new NettyAllocatorMetrics(pooledByteBufAllocator);
        binder.bindTo(this.registry);
        ByteBuf buffer = pooledByteBufAllocator.directBuffer();
        Tags tags = Tags.of("id", String.valueOf(pooledByteBufAllocator.hashCode()), "allocator.type",
                "PooledByteBufAllocator", "memory.type", "direct");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_MEMORY_PINNED.getName()).tags(tags).gauge().value())
            .isPositive();
        buffer.release();
    }

    @Test
    void shouldHaveArenasMetricsForPooledAllocator() {
        PooledByteBufAllocator pooledByteBufAllocator = new PooledByteBufAllocator();
        NettyAllocatorMetrics binder = new NettyAllocatorMetrics(pooledByteBufAllocator);
        binder.bindTo(this.registry);
        ByteBuf buffer = pooledByteBufAllocator.buffer();
        Tags tags = Tags.of("id", String.valueOf(pooledByteBufAllocator.hashCode()), "allocator.type",
                "PooledByteBufAllocator", "memory.type", "heap");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_POOLED_ARENAS.getName()).tags(tags).gauge().value())
            .isEqualTo(pooledByteBufAllocator.metric().numHeapArenas());

        tags = Tags.of("id", String.valueOf(pooledByteBufAllocator.hashCode()), "allocator.type",
                "PooledByteBufAllocator", "memory.type", "direct");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_POOLED_ARENAS.getName()).tags(tags).gauge().value())
            .isEqualTo(pooledByteBufAllocator.metric().numDirectArenas());
        buffer.release();
    }

    @Test
    void shouldHaveCacheSizeMetricsForPooledAllocator() {
        PooledByteBufAllocator pooledByteBufAllocator = new PooledByteBufAllocator();
        NettyAllocatorMetrics binder = new NettyAllocatorMetrics(pooledByteBufAllocator);
        binder.bindTo(this.registry);
        ByteBuf buffer = pooledByteBufAllocator.buffer();
        Tags tags = Tags.of("id", String.valueOf(pooledByteBufAllocator.hashCode()), "allocator.type",
                "PooledByteBufAllocator", "cache.type", "normal");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_POOLED_CACHE_SIZE.getName()).tags(tags).gauge().value())
            .isEqualTo(pooledByteBufAllocator.metric().normalCacheSize());

        tags = Tags.of("id", String.valueOf(pooledByteBufAllocator.hashCode()), "allocator.type",
                "PooledByteBufAllocator", "cache.type", "small");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_POOLED_CACHE_SIZE.getName()).tags(tags).gauge().value())
            .isEqualTo(pooledByteBufAllocator.metric().smallCacheSize());
        buffer.release();
    }

    @Test
    void shouldHaveThreadlocalCachesMetricsForPooledAllocator() {
        PooledByteBufAllocator pooledByteBufAllocator = new PooledByteBufAllocator();
        NettyAllocatorMetrics binder = new NettyAllocatorMetrics(pooledByteBufAllocator);
        binder.bindTo(this.registry);
        ByteBuf buffer = pooledByteBufAllocator.buffer();
        Tags tags = Tags.of("id", String.valueOf(pooledByteBufAllocator.hashCode()), "allocator.type",
                "PooledByteBufAllocator");
        assertThat(
                this.registry.get(NettyMeters.ALLOCATOR_POOLED_THREADLOCAL_CACHES.getName()).tags(tags).gauge().value())
            .isEqualTo(pooledByteBufAllocator.metric().numThreadLocalCaches());
        buffer.release();
    }

    @Test
    void shouldHaveChunkSizeMetricsForPooledAllocator() {
        PooledByteBufAllocator pooledByteBufAllocator = new PooledByteBufAllocator();
        NettyAllocatorMetrics binder = new NettyAllocatorMetrics(pooledByteBufAllocator);
        binder.bindTo(this.registry);
        ByteBuf buffer = pooledByteBufAllocator.buffer();
        Tags tags = Tags.of("id", String.valueOf(pooledByteBufAllocator.hashCode()), "allocator.type",
                "PooledByteBufAllocator");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_POOLED_CHUNK_SIZE.getName()).tags(tags).gauge().value())
            .isEqualTo(pooledByteBufAllocator.metric().chunkSize());
        buffer.release();
    }

}
