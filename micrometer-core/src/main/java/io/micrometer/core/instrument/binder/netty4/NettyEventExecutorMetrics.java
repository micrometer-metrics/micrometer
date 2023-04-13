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
import io.micrometer.core.instrument.binder.MeterBinder;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;

/**
 * {@link MeterBinder} for Netty event executors.
 *
 * @author Brian Clozel
 * @since 1.11.0
 * @see NettyMeters
 */
public class NettyEventExecutorMetrics implements MeterBinder {

    private final Iterable<EventExecutor> eventExecutors;

    /**
     * Create a binder instance for the given event executors.
     * <p>
     * An {@link io.netty.channel.EventLoopGroup} (all its executors) can be instrumented
     * at startup like: <pre>
     * MeterRegistry registry = //...
     * EventLoopGroup group = //...
     * new NettyEventExecutorMetrics(group).bindTo(registry);
     * </pre> Alternatively, an {@link EventLoop} can be instrumented at runtime during
     * channel initialization. In this case, developers should ensure that this instance
     * has not been registered already as re-binding metrics at runtime is inefficient
     * here. <pre>
     * &#064;Override
     * public void initChannel(SocketChannel channel) throws Exception {
     *   // this concurrent check must be implemented by micrometer users
     *   if (!isEventLoopInstrumented(channel.eventLoop())) {
     *     new EventExecutorMetrics(channel.eventLoop()).bindTo(registry);
     *   }
     *   //...
     * }
     * </pre>
     * @param eventExecutors the event executors to instrument
     */
    public NettyEventExecutorMetrics(Iterable<EventExecutor> eventExecutors) {
        this.eventExecutors = eventExecutors;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.eventExecutors.forEach(eventExecutor -> {
            if (eventExecutor instanceof SingleThreadEventExecutor) {
                SingleThreadEventExecutor singleThreadEventExecutor = (SingleThreadEventExecutor) eventExecutor;
                Gauge
                    .builder(NettyMeters.EVENT_EXECUTOR_TASKS_PENDING.getName(),
                            singleThreadEventExecutor::pendingTasks)
                    .tag(NettyMeters.EventExecutorTasksPendingKeyNames.NAME.asString(),
                            singleThreadEventExecutor.threadProperties().name())
                    .register(registry);
            }
        });
    }

}
