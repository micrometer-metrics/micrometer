/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.binder.hystrix;

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.instrument.*;

/**
 * Micrometer publisher for Hystrix thread pool metrics.
 *
 * @deprecated since 1.13.0, Hystrix is no longer in active development, and is currently
 * in maintenance mode.
 * @since 1.2.0
 */
@NonNullApi
@NonNullFields
@Deprecated
public class MicrometerMetricsPublisherThreadPool implements HystrixMetricsPublisherThreadPool {

    private static final String NAME_HYSTRIX_THREADPOOL = "hystrix.threadpool";

    private final MeterRegistry meterRegistry;

    private final HystrixThreadPoolMetrics metrics;

    private final HystrixThreadPoolProperties properties;

    private final HystrixMetricsPublisherThreadPool metricsPublisherForThreadPool;

    private final Tags tags;

    public MicrometerMetricsPublisherThreadPool(final MeterRegistry meterRegistry,
            final HystrixThreadPoolKey threadPoolKey, final HystrixThreadPoolMetrics metrics,
            final HystrixThreadPoolProperties properties,
            final HystrixMetricsPublisherThreadPool metricsPublisherForThreadPool) {
        this.meterRegistry = meterRegistry;
        this.metrics = metrics;
        this.properties = properties;
        this.metricsPublisherForThreadPool = metricsPublisherForThreadPool;

        this.tags = Tags.of("key", threadPoolKey.name());
    }

    @Override
    public void initialize() {
        metricsPublisherForThreadPool.initialize();

        Gauge.builder(metricName("threads.active.current.count"), metrics::getCurrentActiveCount)
            .description("The approximate number of threads that are actively executing tasks.")
            .tags(tags)
            .register(meterRegistry);

        FunctionCounter
            .builder(metricName("threads.cumulative.count"), metrics,
                    HystrixThreadPoolMetrics::getCumulativeCountThreadsExecuted)
            .description("Cumulative count of number of threads since the start of the application.")
            .tags(tags.and(Tag.of("type", "executed")))
            .register(meterRegistry);

        FunctionCounter
            .builder(metricName("threads.cumulative.count"), metrics,
                    HystrixThreadPoolMetrics::getCumulativeCountThreadsRejected)
            .description("Cumulative count of number of threads since the start of the application.")
            .tags(tags.and(Tag.of("type", "rejected")))
            .register(meterRegistry);

        Gauge.builder(metricName("threads.pool.current.size"), metrics::getCurrentPoolSize)
            .description("The current number of threads in the pool.")
            .tags(tags)
            .register(meterRegistry);

        Gauge.builder(metricName("threads.largest.pool.current.size"), metrics::getCurrentLargestPoolSize)
            .description("The largest number of threads that have ever simultaneously been in the pool.")
            .tags(tags)
            .register(meterRegistry);

        Gauge.builder(metricName("threads.max.pool.current.size"), metrics::getCurrentMaximumPoolSize)
            .description("The maximum allowed number of threads.")
            .tags(tags)
            .register(meterRegistry);

        Gauge.builder(metricName("threads.core.pool.current.size"), metrics::getCurrentCorePoolSize)
            .description("The core number of threads.")
            .tags(tags)
            .register(meterRegistry);

        FunctionCounter
            .builder(metricName("tasks.cumulative.count"), metrics, m -> m.getCurrentCompletedTaskCount().longValue())
            .description("The approximate total number of tasks since the start of the application.")
            .tags(tags.and(Tag.of("type", "completed")))
            .register(meterRegistry);

        FunctionCounter.builder(metricName("tasks.cumulative.count"), metrics, m -> m.getCurrentTaskCount().longValue())
            .description("The approximate total number of tasks since the start of the application.")
            .tags(tags.and(Tag.of("type", "scheduled")))
            .register(meterRegistry);

        Gauge.builder(metricName("queue.current.size"), metrics::getCurrentQueueSize)
            .description("Current size of BlockingQueue used by the thread-pool.")
            .tags(tags)
            .register(meterRegistry);

        Gauge.builder(metricName("queue.max.size"), () -> properties.maxQueueSize().get())
            .description("Max size of BlockingQueue used by the thread-pool.")
            .tags(tags)
            .register(meterRegistry);

        Gauge
            .builder(metricName("queue.rejection.threshold.size"), () -> properties.queueSizeRejectionThreshold().get())
            .description(
                    "Artificial max size at which rejections will occur even if maxQueueSize has not been reached.")
            .tags(tags)
            .register(meterRegistry);
    }

    private static String metricName(String name) {
        return String.join(".", NAME_HYSTRIX_THREADPOOL, name);
    }

}
