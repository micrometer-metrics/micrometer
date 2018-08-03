/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNull;

/**
 * @author Manabu Matsuzaki
 */
public class JettyServerThreadPoolMetrics implements MeterBinder {

    private final InstrumentedQueuedThreadPool threadPool;
    private final Iterable<Tag> tags;

    public JettyServerThreadPoolMetrics(InstrumentedQueuedThreadPool threadPool, Iterable<Tag> tags) {
        this.threadPool = threadPool;
        this.tags = tags;
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        Gauge.builder("jetty.threads.config.min", threadPool, InstrumentedQueuedThreadPool::getMinThreads)
             .tags(tags)
             .description("The number of min threads")
             .register(registry);
        Gauge.builder("jetty.threads.config.max", threadPool, InstrumentedQueuedThreadPool::getMaxThreads)
             .tags(tags)
             .description("The number of max threads")
             .register(registry);
        Gauge.builder("jetty.threads.current", threadPool, InstrumentedQueuedThreadPool::getThreads)
             .tags(tags)
             .description("The current number of current threads")
             .register(registry);
        Gauge.builder("jetty.threads.busy", threadPool, InstrumentedQueuedThreadPool::getBusyThreads)
             .tags(tags)
             .description("The current number of busy threads")
             .register(registry);
    }
}
