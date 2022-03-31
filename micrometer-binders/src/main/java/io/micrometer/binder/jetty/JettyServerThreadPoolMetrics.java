/*
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.binder.jetty;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool.SizedThreadPool;

/**
 * {@link MeterBinder} for Jetty {@link ThreadPool}.
 * <p>
 * Pass the {@link ThreadPool} used with the Jetty {@link org.eclipse.jetty.server.Server Server}. For example:
 * <pre>
 *     {@code
 *     QueuedThreadPool threadPool = new QueuedThreadPool();
 *     Server server = new Server(threadPool);
 *     new JettyServerThreadPoolMetrics(threadPool, tags).bindTo(registry);
 *     }
 * </pre>
 *
 * @author Manabu Matsuzaki
 * @author Andy Wilkinson
 * @author Johnny Lim
 * @since 1.1.0
 * @see InstrumentedQueuedThreadPool
 */
public class JettyServerThreadPoolMetrics implements MeterBinder {

    private final ThreadPool threadPool;

    private final Iterable<? extends io.micrometer.common.Tag> tags;

    public JettyServerThreadPoolMetrics(ThreadPool threadPool, Iterable<? extends io.micrometer.common.Tag> tags) {
        this.threadPool = threadPool;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (threadPool instanceof SizedThreadPool) {
            SizedThreadPool sizedThreadPool = (SizedThreadPool) threadPool;
            Gauge.builder("jetty.threads.config.min", sizedThreadPool, SizedThreadPool::getMinThreads)
                    .description("The minimum number of threads in the pool")
                    .tags(tags).register(registry);
            Gauge.builder("jetty.threads.config.max", sizedThreadPool, SizedThreadPool::getMaxThreads)
                    .description("The maximum number of threads in the pool")
                    .tags(tags).register(registry);
            if (threadPool instanceof QueuedThreadPool) {
                QueuedThreadPool queuedThreadPool = (QueuedThreadPool) threadPool;
                Gauge.builder("jetty.threads.busy", queuedThreadPool, QueuedThreadPool::getBusyThreads)
                        .description("The number of busy threads in the pool")
                        .tags(tags).register(registry);
                Gauge.builder("jetty.threads.jobs", queuedThreadPool, QueuedThreadPool::getQueueSize)
                        .description("Number of jobs queued waiting for a thread")
                        .tags(tags).register(registry);
            }
        }
        Gauge.builder("jetty.threads.current", threadPool, ThreadPool::getThreads)
                .description("The total number of threads in the pool")
                .tags(tags).register(registry);
        Gauge.builder("jetty.threads.idle", threadPool, ThreadPool::getIdleThreads)
                .description("The number of idle threads in the pool").tags(tags)
                .register(registry);
    }

}
