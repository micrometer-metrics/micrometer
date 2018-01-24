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
import com.netflix.hystrix.metric.HystrixCommandCompletionStream;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import io.micrometer.core.instrument.*;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Clint Checketts
 */
@NonNullApi
@NonNullFields
public class MicrometerMetricsPublisherCommand implements HystrixMetricsPublisherCommand {
    private static final Logger LOG = LoggerFactory.getLogger(MicrometerMetricsPublisherCommand.class);
    private static final List<HystrixEventType> executionEvents = Arrays.asList(
        HystrixEventType.EMIT,
        HystrixEventType.SUCCESS,
        HystrixEventType.FAILURE,
        HystrixEventType.TIMEOUT,
        HystrixEventType.BAD_REQUEST,
        HystrixEventType.SHORT_CIRCUITED,
        HystrixEventType.THREAD_POOL_REJECTED,
        HystrixEventType.SEMAPHORE_REJECTED);
    private static final List<HystrixEventType> fallbackEvents = Arrays.asList(
        HystrixEventType.FALLBACK_EMIT,
        HystrixEventType.FALLBACK_SUCCESS,
        HystrixEventType.FALLBACK_FAILURE,
        HystrixEventType.FALLBACK_REJECTION,
        HystrixEventType.FALLBACK_MISSING);

    private static final String NAME_HYSTRIX_CIRCUIT_BREAKER_OPEN = "hystrix.circuit.breaker.open";
    private static final String NAME_HYSTRIX_COMMAND_OTHER = "hystrix.command.other";
    private static final String NAME_HYSTRIX_EXECUTION = "hystrix.execution";
    private static final String NAME_HYSTRIX_FALLBACK = "hystrix.fallback";
    private static final String NAME_HYSTRIX_ERRORS = "hystrix.errors";
    private static final String NAME_HYSTRIX_REQUESTS = "hystrix.requests";
    private static final String NAME_HYSTRIX_LATENCY_EXECUTION = "hystrix.latency.execution";
    private static final String NAME_HYSTRIX_LATENCY_TOTAL = "hystrix.latency.total";
    private static final String NAME_HYSTRIX_THREADPOOL_CONCURRENT_EXECUTION_CURRENT = "hystrix.threadpool.concurrent.execution.current";
    private static final String NAME_HYSTRIX_THREADPOOL_CONCURRENT_EXECUTION_ROLLING_MAX = "hystrix.threadpool.concurrent.execution.rolling.max";

    private static final String DESCRIPTION_HYSTRIX_COMMAND_OTHER = "Other execution results. See https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#other-command-event-types-comnetflixhystrixhystrixeventtype for type definitions";
    private static final String DESCRIPTION_HYSTRIX_EXECUTION = "Execution results. See https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#command-execution-event-types-comnetflixhystrixhystrixeventtype for type definitions";
    private static final String DESCRIPTION_HYSTRIX_FALLBACK = "Fallback execution results. See https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#command-fallback-event-types-comnetflixhystrixhystrixeventtype for type definitions";

    private final MeterRegistry meterRegistry;
    private final HystrixCommandMetrics metrics;
    private final HystrixCircuitBreaker circuitBreaker;
    private final Iterable<Tag> tags;
    private final HystrixCommandKey commandKey;

    public MicrometerMetricsPublisherCommand(MeterRegistry meterRegistry, HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        this.meterRegistry = meterRegistry;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreaker;
        this.commandKey = commandKey;

        tags = Tags.zip("group", commandGroupKey.name(), "key", commandKey.name());

        //Initialize commands at zero
        Counter.builder(NAME_HYSTRIX_ERRORS).tags(tags).register(meterRegistry);
        Counter.builder(NAME_HYSTRIX_REQUESTS).tags(tags).register(meterRegistry);
        Timer.builder(NAME_HYSTRIX_LATENCY_EXECUTION).tags(tags).register(meterRegistry);
        Timer.builder(NAME_HYSTRIX_LATENCY_TOTAL).tags(tags).register(meterRegistry);
        executionEvents.forEach(this::getExecutionCounter);
        fallbackEvents.forEach(this::getFallbackCounter);
        Arrays.stream(HystrixEventType.values()).filter(e -> !executionEvents.contains(e) && !fallbackEvents.contains(e))
            .forEach(this::getOtherExecutionCounter);
    }

    @Override
    public void initialize() {
        Gauge.builder(NAME_HYSTRIX_CIRCUIT_BREAKER_OPEN, circuitBreaker, c -> c.isOpen() ? 1 : 0)
            .tags(tags).register(meterRegistry);

        HystrixCommandCompletionStream.getInstance(commandKey)
            .observe()
            .subscribe(hystrixCommandCompletion -> {
                    /*
                     our assumptions about latency as returned by hystrixCommandCompletion:
                     # a latency of >= 0 indicates that this the execution occurred.
                     # a latency of == -1 indicates that the execution didn't occur (default in execution result)
                     # a latency of < -1 indicates some clock problems.
                     We will only count executions, and ignore non-executions with a value of -1.
                     Latencies of < -1 are ignored as they will decrement the counts, and Prometheus will
                     take this as a reset of the counter, therefore this should be avoided by all means.
                     */
                long totalLatency = hystrixCommandCompletion.getTotalLatency();
                if (totalLatency >= 0) {
                    Timer.builder(NAME_HYSTRIX_LATENCY_TOTAL)
                        .tags(tags)
                        .register(meterRegistry)
                        .record(totalLatency, TimeUnit.MILLISECONDS);
                } else if (totalLatency < -1) {
                    LOG.warn("received negative totalLatency, event not counted. " +
                            "This indicates a clock skew? {}",
                        hystrixCommandCompletion);
                }
                long executionLatency = hystrixCommandCompletion.getExecutionLatency();
                if (executionLatency >= 0) {
                    Timer.builder(NAME_HYSTRIX_LATENCY_EXECUTION)
                        .tags(tags)
                        .register(meterRegistry)
                        .record(executionLatency, TimeUnit.MILLISECONDS);
                } else if (executionLatency < -1) {
                    LOG.warn("received negative executionLatency, event not counted. " +
                            "This indicates a clock skew? {}",
                        hystrixCommandCompletion);
                }
                for (HystrixEventType hystrixEventType : HystrixEventType.values()) {
                    int count = hystrixCommandCompletion.getEventCounts().getCount(hystrixEventType);
                    if (count > 0) {
                        switch (hystrixEventType) {
                                /* this list is derived from {@link HystrixCommandMetrics.HealthCounts.plus} */
                            case FAILURE:
                            case TIMEOUT:
                            case THREAD_POOL_REJECTED:
                            case SEMAPHORE_REJECTED:
                                Counter.builder(NAME_HYSTRIX_ERRORS)
                                    .tags(tags)
                                    .register(meterRegistry)
                                    .increment(count);
                            case SUCCESS:
                                Counter.builder(NAME_HYSTRIX_REQUESTS)
                                    .tags(tags)
                                    .register(meterRegistry)
                                    .increment(count);

                                break;
                        }

                        if(executionEvents.contains(hystrixEventType)) {
                            getExecutionCounter(hystrixEventType).increment(count);
                        } else if(fallbackEvents.contains(hystrixEventType)){
                            getFallbackCounter(hystrixEventType).increment(count);
                        } else {
                            getOtherExecutionCounter(hystrixEventType).increment(count);
                        }
                    }
                }
            });

        String threadPool = metrics.getThreadPoolKey().name();
        Gauge.builder(NAME_HYSTRIX_THREADPOOL_CONCURRENT_EXECUTION_CURRENT, metrics, HystrixCommandMetrics::getCurrentConcurrentExecutionCount)
            .tags(Tags.concat(tags, "threadpool", threadPool))
            .register(meterRegistry);
        Gauge.builder(NAME_HYSTRIX_THREADPOOL_CONCURRENT_EXECUTION_ROLLING_MAX, metrics, HystrixCommandMetrics::getRollingMaxConcurrentExecutions)
            .tags(Tags.concat(tags, "threadpool", threadPool))
            .register(meterRegistry);

    }

    private Counter getOtherExecutionCounter(HystrixEventType hystrixEventType) {
        return Counter.builder(NAME_HYSTRIX_COMMAND_OTHER)
            .description(DESCRIPTION_HYSTRIX_COMMAND_OTHER)
            .tags(Tags.concat(tags, "event", hystrixEventType.name().toLowerCase()))
            .register(meterRegistry);
    }

    private Counter getFallbackCounter(HystrixEventType hystrixEventType) {
        return Counter.builder(NAME_HYSTRIX_FALLBACK)
            .description(DESCRIPTION_HYSTRIX_FALLBACK)
            .tags(Tags.concat(tags, "event", hystrixEventType.name().toLowerCase()))
            .register(meterRegistry);
    }

    private Counter getExecutionCounter(HystrixEventType hystrixEventType) {
        return Counter.builder(NAME_HYSTRIX_EXECUTION)
            .description(DESCRIPTION_HYSTRIX_EXECUTION)
            .tags(Tags.concat(tags, "event", hystrixEventType.name().toLowerCase()))
            .register(meterRegistry);
    }

}
