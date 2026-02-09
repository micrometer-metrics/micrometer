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
import io.netty.channel.*;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import static io.micrometer.core.instrument.binder.netty4.NettyMeters.EVENT_EXECUTOR_TASKS_PENDING;
import static io.micrometer.core.instrument.binder.netty4.NettyMeters.EVENT_EXECUTOR_WORKERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link NettyEventExecutorMetrics}.
 *
 * @author Brian Clozel
 */
class NettyEventExecutorMetricsTests {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void shouldHaveTasksPendingMetricForEachEventLoop() {
        Set<String> names = new LinkedHashSet<>();
        try (DefaultEventLoopGroup eventExecutors = new DefaultEventLoopGroup()) {
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
        }
    }

    @Test
    void shouldHaveTasksPendingMetricForSingleEventLoop() {
        try (DefaultEventLoopGroup eventExecutors = new DefaultEventLoopGroup()) {
            EventLoop eventLoop = eventExecutors.next();
            new NettyEventExecutorMetrics(eventLoop).bindTo(this.registry);
            assertThat(eventLoop).isInstanceOf(SingleThreadEventExecutor.class);
            SingleThreadEventExecutor singleThreadEventExecutor = (SingleThreadEventExecutor) eventLoop;
            String eventLoopName = singleThreadEventExecutor.threadProperties().name();
            assertThat(this.registry.get(EVENT_EXECUTOR_TASKS_PENDING.getName())
                .tags(Tags.of("name", eventLoopName))
                .gauge()
                .value()).isZero();
        }
    }

    @Test
    void shouldHaveCustomTags() {
        try (DefaultEventLoopGroup eventExecutors = new DefaultEventLoopGroup()) {
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
        }
    }

    @Test
    void shouldHaveWorkersMetric() {
        try (DefaultEventLoopGroup group = new DefaultEventLoopGroup(4, new DefaultThreadFactory("test-workers"))) {
            new NettyEventExecutorMetrics(group).bindTo(this.registry);

            assertThat(this.registry.get(EVENT_EXECUTOR_WORKERS.getName()).gauge().value()).isEqualTo(4);
        }
    }

    @Test
    void shouldHaveWorkersMetricWithCustomTags() {
        try (DefaultEventLoopGroup group = new DefaultEventLoopGroup(2, new DefaultThreadFactory("test-workers"))) {
            Tags extraTags = Tags.of("testKey", "testValue");
            new NettyEventExecutorMetrics(group, extraTags).bindTo(this.registry);

            assertThat(this.registry.get(EVENT_EXECUTOR_WORKERS.getName()).tags(extraTags).gauge().value())
                .isEqualTo(2);
        }
    }

    @Test
    void shouldCountWorkersForGenericIterableViaFallbackPath() {
        EventExecutor mockExecutor1 = mock(EventExecutor.class);
        EventExecutor mockExecutor2 = mock(EventExecutor.class);
        Iterable<EventExecutor> genericIterable = Arrays.asList(mockExecutor1, mockExecutor2);

        new NettyEventExecutorMetrics(genericIterable).bindTo(this.registry);

        assertThat(this.registry.get(EVENT_EXECUTOR_WORKERS.getName()).gauge().value()).isEqualTo(2.0);
    }

    @Test
    void shouldHaveWorkersMetricForSubclassOfMultiThreadEventLoopGroup() {
        try (EventLoopGroup group = new SubclassOfMultiThreadIoEventLoopGroup(3)) {
            new NettyEventExecutorMetrics(group).bindTo(this.registry);

            assertThat(this.registry.get(EVENT_EXECUTOR_WORKERS.getName()).gauge().value()).isEqualTo(3.0);
        }
    }

    private static class SubclassOfMultiThreadIoEventLoopGroup extends MultithreadEventLoopGroup {

        SubclassOfMultiThreadIoEventLoopGroup(int nThreads) {
            super(nThreads, new DefaultThreadFactory("subclass-test-workers"));
        }

        @Override
        protected EventLoop newChild(Executor executor, Object... objects) throws Exception {
            return new DefaultEventLoop(executor);
        }

    }

}
