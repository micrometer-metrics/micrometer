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
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.local.LocalIoHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.binder.netty4.NettyMeters.EVENT_EXECUTOR_TASKS_PENDING;
import static io.micrometer.core.instrument.binder.netty4.NettyMeters.EVENT_EXECUTOR_WORKERS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NettyEventExecutorMetrics}.
 *
 * @author Brian Clozel
 */
class NettyEventExecutorMetricsTests {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void shouldHaveTasksPendingMetricForEachEventLoop() throws Exception {
        Set<String> names = new LinkedHashSet<>();
        MultithreadEventLoopGroup eventExecutors = new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory());
        new NettyEventExecutorMetrics(eventExecutors).bindTo(this.registry);
        eventExecutors.spliterator().forEachRemaining(eventExecutor -> {
            if (eventExecutor instanceof SingleThreadEventExecutor) {
                SingleThreadEventExecutor singleThreadEventExecutor = (SingleThreadEventExecutor) eventExecutor;
                names.add(singleThreadEventExecutor.threadProperties().name());
            }
        });
        assertThat(names).isNotEmpty();
        names.forEach(name -> {
            assertThat(this.registry.get(EVENT_EXECUTOR_TASKS_PENDING.getName())
                .tags(Tags.of("name", name))
                .gauge()
                .value()).isZero();
        });
        eventExecutors.shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldHaveTasksPendingMetricForSingleEventLoop() throws Exception {
        MultithreadEventLoopGroup eventExecutors = new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory());
        EventLoop eventLoop = eventExecutors.next();
        new NettyEventExecutorMetrics(eventLoop).bindTo(this.registry);
        assertThat(eventLoop).isInstanceOf(SingleThreadEventExecutor.class);
        SingleThreadEventExecutor singleThreadEventExecutor = (SingleThreadEventExecutor) eventLoop;
        String eventLoopName = singleThreadEventExecutor.threadProperties().name();
        assertThat(this.registry.get(EVENT_EXECUTOR_TASKS_PENDING.getName())
            .tags(Tags.of("name", eventLoopName))
            .gauge()
            .value()).isZero();
        eventExecutors.shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldHaveCustomTags() throws Exception {
        MultithreadEventLoopGroup eventExecutors = new MultiThreadIoEventLoopGroup(LocalIoHandler.newFactory());
        EventLoop eventLoop = eventExecutors.next();
        Tags extraTags = Tags.of("testKey", "testValue");
        new NettyEventExecutorMetrics(eventLoop, extraTags).bindTo(this.registry);
        assertThat(eventLoop).isInstanceOf(SingleThreadEventExecutor.class);
        SingleThreadEventExecutor singleThreadEventExecutor = (SingleThreadEventExecutor) eventLoop;
        String eventLoopName = singleThreadEventExecutor.threadProperties().name();
        assertThat(this.registry.get(EVENT_EXECUTOR_TASKS_PENDING.getName())
            .tags(Tags.of("name", eventLoopName))
            .tags(extraTags)
            .gauge()
            .value()).isZero();
        eventExecutors.shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldHaveWorkersMetric() throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(4, new DefaultThreadFactory("test-workers"),
                LocalIoHandler.newFactory());
        new NettyEventExecutorMetrics(group).bindTo(this.registry);

        assertThat(this.registry.get(EVENT_EXECUTOR_WORKERS.getName()).gauge().value()).isEqualTo(4);

        group.shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldHaveWorkersMetricWithCustomTags() throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(2, new DefaultThreadFactory("test-workers"),
                LocalIoHandler.newFactory());
        Tags extraTags = Tags.of("testKey", "testValue");
        new NettyEventExecutorMetrics(group, extraTags).bindTo(this.registry);

        assertThat(this.registry.get(EVENT_EXECUTOR_WORKERS.getName()).tags(extraTags).gauge().value()).isEqualTo(2);

        group.shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldHaveWorkersMetricForNonMultithreadEventLoopGroup() throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(4, new DefaultThreadFactory("test-workers"),
                LocalIoHandler.newFactory());
        EventLoop singleLoop = group.next();
        new NettyEventExecutorMetrics(Collections.singletonList(singleLoop)).bindTo(this.registry);

        assertThat(this.registry.get(EVENT_EXECUTOR_WORKERS.getName()).gauge().value()).isEqualTo(1.0);

        group.shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldHaveWorkersMetricForSubclassOfMultiThreadEventLoopGroup() throws Exception {
        EventLoopGroup group = new SubclassOfMultiThreadIoEventLoopGroup(3);
        new NettyEventExecutorMetrics(group).bindTo(this.registry);

        assertThat(this.registry.get(EVENT_EXECUTOR_WORKERS.getName()).gauge().value()).isEqualTo(3.0);

        group.shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

    private static class SubclassOfMultiThreadIoEventLoopGroup extends MultiThreadIoEventLoopGroup {

        SubclassOfMultiThreadIoEventLoopGroup(int nThreads) {
            super(nThreads, new DefaultThreadFactory("subclass-test-workers"), LocalIoHandler.newFactory());
        }

    }

}
