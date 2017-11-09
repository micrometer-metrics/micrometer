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

import com.netflix.hystrix.*;
import com.netflix.hystrix.metric.consumer.CumulativeCommandEventCounterStream;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * @author Clint Checketts
 */
public class MicrometerMetricsPublisherCommand implements HystrixMetricsPublisherCommand {
    private static final Logger LOG = LoggerFactory.getLogger(MicrometerMetricsPublisherCommand.class);

    private final MeterRegistry meterRegistry;
    private final HystrixCommandMetrics metrics;
    private final HystrixCircuitBreaker circuitBreaker;
    private final List<Tag> tags;
    private final HystrixCommandProperties properties;
    private final HystrixCommandKey commandKey;

    public MicrometerMetricsPublisherCommand(MeterRegistry meterRegistry, HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        this.meterRegistry = meterRegistry;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreaker;
        this.commandKey = commandKey;
        this.properties = properties;
        tags = Tags.zip("group", commandGroupKey.name(), "key", commandKey.name());
    }

    @Override
    public void initialize() {
        Gauge.builder("hystrix.circuit.breaker.open", circuitBreaker, c -> c.isOpen() ? 1 : 0)
            .tags(tags).register(meterRegistry);

        String executionName = "hystrix.execution";
        String executionDescription = "Execution results. See https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#command-execution-event-types-comnetflixhystrixhystrixeventtype for type definitions";
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.EMIT);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.SUCCESS);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.FAILURE);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.TIMEOUT);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.BAD_REQUEST);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.SHORT_CIRCUITED);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
        createCounter(executionName, executionDescription, HystrixRollingNumberEvent.SEMAPHORE_REJECTED);


        String fallbackEventName = "hystrix.fallback";
        String fallbackEventDescription = "Fallback execution results. See https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#command-fallback-event-types-comnetflixhystrixhystrixeventtype for type definitions";
        createCounter(fallbackEventName, fallbackEventDescription, HystrixRollingNumberEvent.FALLBACK_EMIT);
        createCounter(fallbackEventName, fallbackEventDescription, HystrixRollingNumberEvent.FALLBACK_SUCCESS);
        createCounter(fallbackEventName, fallbackEventDescription, HystrixRollingNumberEvent.FALLBACK_FAILURE);
        createCounter(fallbackEventName, fallbackEventDescription, HystrixRollingNumberEvent.FALLBACK_REJECTION);
        createCounter(fallbackEventName, fallbackEventDescription, HystrixRollingNumberEvent.FALLBACK_MISSING);

        CumulativeCommandEventCounterStream.getInstance(commandKey, properties).startCachingStreamValuesIfUnstarted();
    }

    private void createCounter(String name, String executionDescription, HystrixRollingNumberEvent event) {
        ToDoubleFunction<HystrixCommandMetrics> getCumulativeCount = m -> {
            try {
                return m.getCumulativeCount(event);
            } catch (NoSuchFieldError error) {
                LOG.error("While publishing metrics, error looking up eventType for : {}.  Please check that all Hystrix versions are the same!", name);
                return 0L;
            }
        };

        FunctionCounter.builder(name, metrics, getCumulativeCount).description(executionDescription)
            .tags(Tags.concat(tags, "event", event.name().toLowerCase()))
            .register(meterRegistry);
    }

}
