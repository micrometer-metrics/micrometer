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
import com.netflix.hystrix.metric.HystrixCommandCompletionStream;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @deprecated since 1.13.0, Hystrix is no longer in active development, and is currently
 * in maintenance mode.
 * @author Clint Checketts
 */
@NonNullApi
@NonNullFields
@Deprecated
public class MicrometerMetricsPublisherCommand implements HystrixMetricsPublisherCommand {

    private static final InternalLogger LOG = InternalLoggerFactory
        .getInstance(MicrometerMetricsPublisherCommand.class);

    private static final String NAME_HYSTRIX_CIRCUIT_BREAKER_OPEN = "hystrix.circuit.breaker.open";

    private static final String NAME_HYSTRIX_EXECUTION = "hystrix.execution";

    private static final String NAME_HYSTRIX_EXECUTION_TERMINAL_TOTAL = "hystrix.execution.terminal";

    private static final String NAME_HYSTRIX_LATENCY_EXECUTION = "hystrix.latency.execution";

    private static final String NAME_HYSTRIX_LATENCY_TOTAL = "hystrix.latency.total";

    private static final String NAME_HYSTRIX_CONCURRENT_EXECUTION_CURRENT = "hystrix.concurrent.execution.current";

    private static final String NAME_HYSTRIX_CONCURRENT_EXECUTION_ROLLING_MAX = "hystrix.concurrent.execution.rolling.max";

    private static final String DESCRIPTION_HYSTRIX_EXECUTION = "Execution results. See https://github.com/Netflix/Hystrix/wiki/Metrics-and-Monitoring#command-execution-event-types-comnetflixhystrixhystrixeventtype for type definitions";

    private static final String DESCRIPTION_HYSTRIX_EXECUTION_TERMINAL_TOTAL = "Sum of all terminal executions. Use this to derive percentages from hystrix.execution";

    private final MeterRegistry meterRegistry;

    private final HystrixCommandMetrics metrics;

    private final HystrixCircuitBreaker circuitBreaker;

    private final Iterable<Tag> tags;

    private final HystrixCommandKey commandKey;

    private HystrixMetricsPublisherCommand metricsPublisherForCommand;

    public MicrometerMetricsPublisherCommand(MeterRegistry meterRegistry, HystrixCommandKey commandKey,
            HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker,
            HystrixMetricsPublisherCommand metricsPublisherForCommand) {
        this.meterRegistry = meterRegistry;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreaker;
        this.commandKey = commandKey;
        this.metricsPublisherForCommand = metricsPublisherForCommand;

        tags = Tags.of("group", commandGroupKey.name(), "key", commandKey.name());
    }

    @Override
    public void initialize() {
        metricsPublisherForCommand.initialize();
        Gauge.builder(NAME_HYSTRIX_CIRCUIT_BREAKER_OPEN, circuitBreaker, c -> c.isOpen() ? 1 : 0)
            .tags(tags)
            .register(meterRegistry);

        // initialize all commands counters and timers with zero
        final Map<HystrixEventType, Counter> eventCounters = new HashMap<>();
        Arrays.asList(HystrixEventType.values()).forEach(hystrixEventType -> {
            eventCounters.put(hystrixEventType, getCounter(hystrixEventType));
        });

        Counter terminalEventCounterTotal = Counter.builder(NAME_HYSTRIX_EXECUTION_TERMINAL_TOTAL)
            .description(DESCRIPTION_HYSTRIX_EXECUTION_TERMINAL_TOTAL)
            .tags(Tags.concat(tags))
            .register(meterRegistry);

        final Timer latencyExecution = Timer.builder(NAME_HYSTRIX_LATENCY_EXECUTION).tags(tags).register(meterRegistry);
        final Timer latencyTotal = Timer.builder(NAME_HYSTRIX_LATENCY_TOTAL).tags(tags).register(meterRegistry);

        HystrixCommandCompletionStream.getInstance(commandKey).observe().subscribe(hystrixCommandCompletion -> {
            // @formatter:off
            /*
             * our assumptions about latency as returned by hystrixCommandCompletion:
             * # a latency of >= 0 indicates that this the execution occurred.
             * # a latency of == -1 indicates that the execution didn't occur (default
             * in execution result)
             * # a latency of < -1 indicates some clock problems.
             * We will only count executions, and ignore non-executions with a value of -1.
             * Latencies of < -1 are ignored as they will decrement the counts, and
             * Prometheus will take this as a reset of the counter, therefore this
             * should be avoided by all means.
             */
            // @formatter:on
            long totalLatency = hystrixCommandCompletion.getTotalLatency();
            if (totalLatency >= 0) {
                latencyTotal.record(totalLatency, TimeUnit.MILLISECONDS);
            }
            else if (totalLatency < -1) {
                LOG.warn("received negative totalLatency, event not counted. " + "This indicates a clock skew? {}",
                        hystrixCommandCompletion);
            }
            long executionLatency = hystrixCommandCompletion.getExecutionLatency();
            if (executionLatency >= 0) {
                latencyExecution.record(executionLatency, TimeUnit.MILLISECONDS);
            }
            else if (executionLatency < -1) {
                LOG.warn("received negative executionLatency, event not counted. " + "This indicates a clock skew? {}",
                        hystrixCommandCompletion);
            }
            for (HystrixEventType hystrixEventType : HystrixEventType.values()) {
                int count = hystrixCommandCompletion.getEventCounts().getCount(hystrixEventType);
                if (count > 0) {
                    eventCounters.get(hystrixEventType).increment(count);
                    if (hystrixEventType.isTerminal()) {
                        terminalEventCounterTotal.increment(count);
                    }
                }
            }
        });

        Gauge
            .builder(NAME_HYSTRIX_CONCURRENT_EXECUTION_CURRENT, metrics,
                    HystrixCommandMetrics::getCurrentConcurrentExecutionCount)
            .tags(tags)
            .register(meterRegistry);

        Gauge
            .builder(NAME_HYSTRIX_CONCURRENT_EXECUTION_ROLLING_MAX, metrics,
                    HystrixCommandMetrics::getRollingMaxConcurrentExecutions)
            .tags(tags)
            .register(meterRegistry);
    }

    private Counter getCounter(HystrixEventType hystrixEventType) {
        return Counter.builder(NAME_HYSTRIX_EXECUTION)
            .description(DESCRIPTION_HYSTRIX_EXECUTION)
            .tags(Tags.concat(tags, "event", hystrixEventType.name().toLowerCase(Locale.ROOT), "terminal",
                    Boolean.toString(hystrixEventType.isTerminal())))
            .register(meterRegistry);
    }

}
