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

import com.netflix.hystrix.*;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

/**
 * @author Clint Checketts
 */
@NonNullApi
@NonNullFields
public class MicrometerMetricsPublisher extends HystrixMetricsPublisher {
    private final MeterRegistry registry;
    private HystrixMetricsPublisher metricsPublisher;

    public MicrometerMetricsPublisher(MeterRegistry registry, HystrixMetricsPublisher metricsPublisher) {
        this.registry = registry;
        this.metricsPublisher = metricsPublisher;
    }

    @Override
    public HystrixMetricsPublisherThreadPool getMetricsPublisherForThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
        final HystrixMetricsPublisherThreadPool metricsPublisherForThreadPool =
            metricsPublisher.getMetricsPublisherForThreadPool(threadPoolKey, metrics, properties);
        return new MicrometerMetricsPublisherThreadPool(registry, threadPoolKey, metrics, properties, metricsPublisherForThreadPool);
    }

    @Override
    public HystrixMetricsPublisherCollapser getMetricsPublisherForCollapser(HystrixCollapserKey collapserKey, HystrixCollapserMetrics metrics, HystrixCollapserProperties properties) {
        return metricsPublisher.getMetricsPublisherForCollapser(collapserKey, metrics, properties);
    }

    @Override
    public HystrixMetricsPublisherCommand getMetricsPublisherForCommand(HystrixCommandKey commandKey,
                                                                        HystrixCommandGroupKey commandGroupKey,
                                                                        HystrixCommandMetrics metrics,
                                                                        HystrixCircuitBreaker circuitBreaker,
                                                                        HystrixCommandProperties properties) {
        HystrixMetricsPublisherCommand metricsPublisherForCommand =
            metricsPublisher.getMetricsPublisherForCommand(commandKey, commandGroupKey, metrics, circuitBreaker, properties);
        return new MicrometerMetricsPublisherCommand(registry, commandKey, commandGroupKey, metrics, circuitBreaker, metricsPublisherForCommand);
    }
}
