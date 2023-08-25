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
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
        DefaultEventLoopGroup eventExecutors = new DefaultEventLoopGroup();
        new NettyEventExecutorMetrics(eventExecutors).bindTo(this.registry);
        eventExecutors.spliterator().forEachRemaining(eventExecutor -> {
            if (eventExecutor instanceof SingleThreadEventExecutor) {
                SingleThreadEventExecutor singleThreadEventExecutor = (SingleThreadEventExecutor) eventExecutor;
                names.add(singleThreadEventExecutor.threadProperties().name());
            }
        });
        assertThat(names).isNotEmpty();
        names.forEach(name -> {
            assertThat(this.registry.get(NettyMeters.EVENT_EXECUTOR_TASKS_PENDING.getName())
                .tags(Tags.of("name", name))
                .gauge()
                .value()).isZero();
        });
        eventExecutors.shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldHaveTasksPendingMetricForSingleEventLoop() throws Exception {
        DefaultEventLoopGroup eventExecutors = new DefaultEventLoopGroup();
        EventLoop eventLoop = eventExecutors.next();
        new NettyEventExecutorMetrics(eventLoop).bindTo(this.registry);
        assertThat(eventLoop).isInstanceOf(SingleThreadEventExecutor.class);
        SingleThreadEventExecutor singleThreadEventExecutor = (SingleThreadEventExecutor) eventLoop;
        String eventLoopName = singleThreadEventExecutor.threadProperties().name();
        assertThat(this.registry.get(NettyMeters.EVENT_EXECUTOR_TASKS_PENDING.getName())
            .tags(Tags.of("name", eventLoopName))
            .gauge()
            .value()).isZero();
        eventExecutors.shutdownGracefully().get(5, TimeUnit.SECONDS);
    }

}
