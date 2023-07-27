/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.util.concurrent.BlockingQueue;

/**
 * A {@link QueuedThreadPool} that binds metrics about the Jetty server thread pool. This
 * can be passed when constructing a Jetty server. For example:
 *
 * <pre>{@code
 *     Server server = new Server(new InstrumentedQueuedThreadPool(registry, Tags.empty()));
 *     // ...
 * }</pre>
 *
 * @since 1.1.0
 * @see JettyServerThreadPoolMetrics
 */
public class InstrumentedQueuedThreadPool extends QueuedThreadPool {

    private final MeterRegistry registry;

    private final Iterable<Tag> tags;

    @Nullable
    private JettyServerThreadPoolMetrics threadPoolMetrics;

    /**
     * Default values for the instrumented thread pool.
     * @param registry where metrics will be bound
     * @param tags tags to apply to metrics bound from this
     */
    public InstrumentedQueuedThreadPool(MeterRegistry registry, Iterable<Tag> tags) {
        this.registry = registry;
        this.tags = tags;
    }

    /**
     * Instrumented thread pool.
     * @param registry where metrics will be bound
     * @param tags tags to apply to metrics bound from this
     * @param maxThreads maximum threads for the thread pool
     * @since 1.5.0
     */
    public InstrumentedQueuedThreadPool(MeterRegistry registry, Iterable<Tag> tags, int maxThreads) {
        super(maxThreads);
        this.registry = registry;
        this.tags = tags;
    }

    /**
     * Instrumented thread pool.
     * @param registry where metrics will be bound
     * @param tags tags to apply to metrics bound from this
     * @param maxThreads maximum threads for the thread pool
     * @param minThreads minimum threads for the thread pool
     * @since 1.5.0
     */
    public InstrumentedQueuedThreadPool(MeterRegistry registry, Iterable<Tag> tags, int maxThreads, int minThreads) {
        super(maxThreads, minThreads);
        this.registry = registry;
        this.tags = tags;
    }

    /**
     * Instrumented thread pool.
     * @param registry where metrics will be bound
     * @param tags tags to apply to metrics bound from this
     * @param maxThreads maximum threads for the thread pool
     * @param minThreads minimum threads for the thread pool
     * @param idleTimeout timeout for idle threads in pool
     * @since 1.5.0
     */
    public InstrumentedQueuedThreadPool(MeterRegistry registry, Iterable<Tag> tags, int maxThreads, int minThreads,
            int idleTimeout) {
        super(maxThreads, minThreads, idleTimeout);
        this.registry = registry;
        this.tags = tags;
    }

    /**
     * Instrumented thread pool.
     * @param registry where metrics will be bound
     * @param tags tags to apply to metrics bound from this
     * @param maxThreads maximum threads for the thread pool
     * @param minThreads minimum threads for the thread pool
     * @param idleTimeout timeout for idle threads in pool
     * @param queue backing queue for thread pool tasks
     * @since 1.5.0
     */
    public InstrumentedQueuedThreadPool(MeterRegistry registry, Iterable<Tag> tags, int maxThreads, int minThreads,
            int idleTimeout, BlockingQueue<Runnable> queue) {
        super(maxThreads, minThreads, idleTimeout, queue);
        this.registry = registry;
        this.tags = tags;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        threadPoolMetrics = new JettyServerThreadPoolMetrics(this, tags);
        threadPoolMetrics.bindTo(registry);
    }

    @Override
    protected void doStop() throws Exception {
        if (threadPoolMetrics != null) {
            threadPoolMetrics.close();
        }
        super.doStop();
    }

}
