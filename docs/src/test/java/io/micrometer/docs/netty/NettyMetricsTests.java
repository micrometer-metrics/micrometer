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
package io.micrometer.docs.netty;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.netty4.NettyAllocatorMetrics;
import io.micrometer.core.instrument.binder.netty4.NettyEventExecutorMetrics;
import io.micrometer.core.instrument.binder.netty4.NettyMeters;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufAllocatorMetricProvider;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sources for netty/index.adoc
 */
class NettyMetricsTests {

    private SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    @Test
    void directInstrumentationExample() throws Exception {
        Set<String> names = new LinkedHashSet<>();
        // tag::directInstrumentation[]
        // Create or get an existing resources
        DefaultEventLoopGroup eventExecutors = new DefaultEventLoopGroup();
        UnpooledByteBufAllocator unpooledByteBufAllocator = new UnpooledByteBufAllocator(false);
        // Use binders to instrument them
        new NettyEventExecutorMetrics(eventExecutors).bindTo(this.registry);
        new NettyAllocatorMetrics(unpooledByteBufAllocator).bindTo(this.registry);
        // end::directInstrumentation[]
        eventExecutors.spliterator().forEachRemaining(eventExecutor -> {
            if (eventExecutor instanceof SingleThreadEventExecutor) {
                SingleThreadEventExecutor singleThreadEventExecutor = (SingleThreadEventExecutor) eventExecutor;
                names.add(singleThreadEventExecutor.threadProperties().name());
            }
        });
        names.forEach(name -> {
            assertThat(this.registry.get(NettyMeters.EVENT_EXECUTOR_TASKS_PENDING.getName())
                .tags(Tags.of("name", name))
                .gauge()
                .value()).isZero();
        });
        eventExecutors.shutdownGracefully().get(5, TimeUnit.SECONDS);

        ByteBuf buffer = unpooledByteBufAllocator.buffer();
        Tags tags = Tags.of("id", String.valueOf(unpooledByteBufAllocator.hashCode()), "allocator.type",
                "UnpooledByteBufAllocator", "memory.type", "heap");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_MEMORY_USED.getName()).tags(tags).gauge().value())
            .isPositive();
        buffer.release();
    }

    @Test
    void shouldInstrumentEventLoopDuringChannelInit() throws Exception {
        NioEventLoopGroup eventExecutors = new NioEventLoopGroup();
        CustomChannelInitializer channelInitializer = new CustomChannelInitializer(this.registry);
        NioSocketChannel channel = new NioSocketChannel();
        eventExecutors.register(channel).await();
        channelInitializer.initChannel(channel);
        assertThat(this.registry.get(NettyMeters.EVENT_EXECUTOR_TASKS_PENDING.getName())
            .tags(Tags.of("name", channel.eventLoop().threadProperties().name()))
            .gauge()
            .value()).isZero();
        ByteBuf buffer = channel.alloc().buffer();
        Tags tags = Tags.of("id", String.valueOf(channel.alloc().hashCode()), "allocator.type",
                "PooledByteBufAllocator", "memory.type", "direct");
        assertThat(this.registry.get(NettyMeters.ALLOCATOR_MEMORY_USED.getName()).tags(tags).gauge().value())
            .isPositive();
        buffer.release();
        eventExecutors.shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

    static class CustomChannelInitializer extends ChannelInitializer<SocketChannel> {

        private final MeterRegistry meterRegistry;

        public CustomChannelInitializer(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        // tag::channelInstrumentation[]
        @Override
        protected void initChannel(SocketChannel channel) throws Exception {
            EventLoop eventLoop = channel.eventLoop();
            if (!isEventLoopInstrumented(eventLoop)) {
                new NettyEventExecutorMetrics(eventLoop).bindTo(this.meterRegistry);
            }
            ByteBufAllocator allocator = channel.alloc();
            if (!isAllocatorInstrumented(allocator) && allocator instanceof ByteBufAllocatorMetricProvider) {
                ByteBufAllocatorMetricProvider allocatorMetric = (ByteBufAllocatorMetricProvider) allocator;
                new NettyAllocatorMetrics(allocatorMetric).bindTo(this.meterRegistry);
            }
        }
        // end::channelInstrumentation[]

        private boolean isEventLoopInstrumented(EventLoop eventLoop) {
            return false;
        }

        private boolean isAllocatorInstrumented(ByteBufAllocator allocator) {
            return false;
        }

    }

}
