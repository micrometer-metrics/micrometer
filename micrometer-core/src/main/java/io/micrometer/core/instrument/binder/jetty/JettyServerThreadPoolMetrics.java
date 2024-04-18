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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool.SizedThreadPool;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link MeterBinder} for Jetty {@link ThreadPool}.
 * <p>
 * Pass the {@link ThreadPool} used with the Jetty {@link org.eclipse.jetty.server.Server
 * Server}. For example: <pre>
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
public class JettyServerThreadPoolMetrics implements MeterBinder, AutoCloseable {

    private static final String MIN = "jetty.threads.config.min";

    private static final String MAX = "jetty.threads.config.max";

    private static final String BUSY = "jetty.threads.busy";

    private static final String JOBS = "jetty.threads.jobs";

    private static final String CURRENT = "jetty.threads.current";

    private static final String IDLE = "jetty.threads.idle";

    private final ThreadPool threadPool;

    private final Iterable<Tag> tags;

    private final Set<Meter.Id> registeredMeterIds = ConcurrentHashMap.newKeySet();

    @Nullable
    private MeterRegistry registry;

    public JettyServerThreadPoolMetrics(ThreadPool threadPool, Iterable<Tag> tags) {
        this.threadPool = threadPool;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;
        if (threadPool instanceof SizedThreadPool) {
            SizedThreadPool sizedThreadPool = (SizedThreadPool) threadPool;
            Gauge minGauge = Gauge.builder(MIN, sizedThreadPool, SizedThreadPool::getMinThreads)
                .description("The minimum number of threads in the pool")
                .tags(tags)
                .register(registry);
            registeredMeterIds.add(minGauge.getId());
            Gauge maxGauge = Gauge.builder(MAX, sizedThreadPool, SizedThreadPool::getMaxThreads)
                .description("The maximum number of threads in the pool")
                .tags(tags)
                .register(registry);
            registeredMeterIds.add(maxGauge.getId());
            if (threadPool instanceof QueuedThreadPool) {
                QueuedThreadPool queuedThreadPool = (QueuedThreadPool) threadPool;
                Gauge busyGauge = Gauge.builder(BUSY, queuedThreadPool, QueuedThreadPool::getBusyThreads)
                    .description("The number of busy threads in the pool")
                    .tags(tags)
                    .register(registry);
                registeredMeterIds.add(busyGauge.getId());
                Gauge jobsGauge = Gauge.builder(JOBS, queuedThreadPool, QueuedThreadPool::getQueueSize)
                    .description("Number of jobs queued waiting for a thread")
                    .tags(tags)
                    .register(registry);
                registeredMeterIds.add(jobsGauge.getId());
            }
        }
        Gauge currentGauge = Gauge.builder(CURRENT, threadPool, ThreadPool::getThreads)
            .description("The total number of threads in the pool")
            .tags(tags)
            .register(registry);
        registeredMeterIds.add(currentGauge.getId());
        Gauge idleGauge = Gauge.builder(IDLE, threadPool, ThreadPool::getIdleThreads)
            .description("The number of idle threads in the pool")
            .tags(tags)
            .register(registry);
        registeredMeterIds.add(idleGauge.getId());
    }

    @Override
    public void close() throws Exception {
        if (registry != null) {
            registeredMeterIds.forEach(registry::remove);
            registeredMeterIds.clear();
        }
    }

}
