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
package io.micrometer.core.instrument.binder.hystrix;

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

/**
 * Micrometer publisher for Hystrix thread pool metrics.
 */
@NonNullApi
@NonNullFields
public class MicrometerMetricsPublisherThreadPool implements HystrixMetricsPublisherThreadPool {
  private static final String NAME_HYSTRIX_THREADPOOL = "hystrix.threadpool";

  private final MeterRegistry meterRegistry;
  private final HystrixThreadPoolKey threadPoolKey;
  private final HystrixThreadPoolMetrics metrics;
  private final HystrixThreadPoolProperties properties;
  private final HystrixMetricsPublisherThreadPool metricsPublisherForThreadPool;
  private final Tags tags;

  public MicrometerMetricsPublisherThreadPool(
      final MeterRegistry meterRegistry,
      final HystrixThreadPoolKey threadPoolKey,
      final HystrixThreadPoolMetrics metrics,
      final HystrixThreadPoolProperties properties,
      final HystrixMetricsPublisherThreadPool metricsPublisherForThreadPool) {
    this.meterRegistry = meterRegistry;
    this.threadPoolKey = threadPoolKey;
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

    Gauge.builder(metricName("threads.active.rolling.max"), metrics::getRollingMaxActiveThreads)
        .description("Rolling max number of active threads during rolling statistical window.")
        .tags(tags)
        .register(meterRegistry);

    Gauge.builder(metricName("threads.executed.cumulative.count"), metrics::getCumulativeCountThreadsExecuted)
        .description("Cumulative count of number of threads executed since the start of the application.")
        .tags(tags)
        .register(meterRegistry);

    Gauge.builder(metricName("threads.rejected.cumulative.count"), metrics::getCumulativeCountThreadsRejected)
        .description("Cumulative count of number of threads rejected since the start of the application.")
        .tags(tags)
        .register(meterRegistry);

    Gauge.builder(metricName("threads.executed.rolling.count"), metrics::getRollingCountThreadsExecuted)
        .description("Rolling count of number of threads executed during rolling statistical window.")
        .tags(tags)
        .register(meterRegistry);

    Gauge.builder(metricName("threads.rejected.rolling.count"), metrics::getRollingCountThreadsRejected)
        .description("Rolling count of number of threads rejected during rolling statistical window.")
        .tags(tags)
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

    Gauge.builder(metricName("tasks.completed.cumulative.count"), metrics::getCurrentCompletedTaskCount)
        .description("The approximate total number of tasks that have completed execution.")
        .tags(tags)
        .register(meterRegistry);

    Gauge.builder(metricName("tasks.scheduled.cumulative.count"), metrics::getCurrentTaskCount)
        .description("The approximate total number of tasks that have ever been scheduled for execution.")
        .tags(tags)
        .register(meterRegistry);

    Gauge.builder(metricName("queue.current.size"), metrics::getCurrentQueueSize)
        .description("Current size of BlockingQueue used by the thread-pool.")
        .tags(tags)
        .register(meterRegistry);

    Gauge.builder(metricName("queue.max.size"), () -> properties.maxQueueSize().get())
        .description("Max size of BlockingQueue used by the thread-pool.")
        .tags(tags)
        .register(meterRegistry);

    Gauge.builder(metricName("queue.rejection.threshold.size"), () -> properties.queueSizeRejectionThreshold().get())
        .description("Artificial max size at which rejections will occur even if maxQueueSize has not been reached.")
        .tags(tags)
        .register(meterRegistry);
  }

  private static String metricName(String name) {
    return String.join(".", NAME_HYSTRIX_THREADPOOL, name);
  }
}
