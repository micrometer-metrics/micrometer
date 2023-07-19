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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.eclipse.jetty.server.handler.StatisticsHandler;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * @deprecated Since 1.4.0. Use {@link TimedHandler} instead.
 */
@Deprecated
@NonNullApi
@NonNullFields
public class JettyStatisticsMetrics implements MeterBinder {

    private final StatisticsHandler statisticsHandler;

    private Iterable<Tag> tags;

    public JettyStatisticsMetrics(StatisticsHandler statisticsHandler, Iterable<Tag> tags) {
        this.tags = tags;
        this.statisticsHandler = statisticsHandler;
    }

    public static void monitor(MeterRegistry meterRegistry, StatisticsHandler statisticsHandler, String... tags) {
        monitor(meterRegistry, statisticsHandler, Tags.of(tags));
    }

    public static void monitor(MeterRegistry meterRegistry, StatisticsHandler statisticsHandler, Iterable<Tag> tags) {
        new JettyStatisticsMetrics(statisticsHandler, tags).bindTo(meterRegistry);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        bindTimer(registry, "jetty.requests", "Request duration", StatisticsHandler::getRequests,
                StatisticsHandler::getRequestTimeTotal);
        bindTimer(registry, "jetty.dispatched", "Dispatch duration", StatisticsHandler::getDispatched,
                StatisticsHandler::getDispatchedTimeTotal);

        bindCounter(registry, "jetty.async.requests", "Total number of async requests",
                StatisticsHandler::getAsyncRequests);
        bindCounter(registry, "jetty.async.dispatches",
                "Total number of requests that have been asynchronously dispatched",
                StatisticsHandler::getAsyncDispatches);
        bindCounter(registry, "jetty.async.expires", "Total number of async requests that have expired",
                StatisticsHandler::getExpires);
        FunctionCounter.builder("jetty.responses.size", statisticsHandler, StatisticsHandler::getResponsesBytesTotal)
            .description("Total number of bytes across all responses")
            .baseUnit(BaseUnits.BYTES)
            .tags(tags)
            .register(registry);

        bindGauge(registry, "jetty.requests.active", "Number of requests currently active",
                StatisticsHandler::getRequestsActive);
        bindGauge(registry, "jetty.dispatched.active", "Number of dispatches currently active",
                StatisticsHandler::getDispatchedActive);
        bindGauge(registry, "jetty.dispatched.active.max", "Maximum number of active dispatches being handled",
                StatisticsHandler::getDispatchedActiveMax);

        bindTimeGauge(registry, "jetty.dispatched.time.max", "Maximum time spent in dispatch handling",
                StatisticsHandler::getDispatchedTimeMax);

        bindGauge(registry, "jetty.async.requests.waiting", "Currently waiting async requests",
                StatisticsHandler::getAsyncRequestsWaiting);
        bindGauge(registry, "jetty.async.requests.waiting.max", "Maximum number of waiting async requests",
                StatisticsHandler::getAsyncRequestsWaitingMax);

        bindTimeGauge(registry, "jetty.request.time.max", "Maximum time spent handling requests",
                StatisticsHandler::getRequestTimeMax);
        bindTimeGauge(registry, "jetty.stats", "Time stats have been collected for", StatisticsHandler::getStatsOnMs);

        bindStatusCounters(registry);
    }

    private void bindStatusCounters(MeterRegistry registry) {
        buildStatusCounter(registry, "1xx", StatisticsHandler::getResponses1xx);
        buildStatusCounter(registry, "2xx", StatisticsHandler::getResponses2xx);
        buildStatusCounter(registry, "3xx", StatisticsHandler::getResponses3xx);
        buildStatusCounter(registry, "4xx", StatisticsHandler::getResponses4xx);
        buildStatusCounter(registry, "5xx", StatisticsHandler::getResponses5xx);
    }

    private void bindGauge(MeterRegistry registry, String name, String description,
            ToDoubleFunction<StatisticsHandler> valueFunction) {
        Gauge.builder(name, statisticsHandler, valueFunction).tags(tags).description(description).register(registry);
    }

    private void bindTimer(MeterRegistry registry, String name, String desc,
            ToLongFunction<StatisticsHandler> countFunc, ToDoubleFunction<StatisticsHandler> consumer) {
        FunctionTimer.builder(name, statisticsHandler, countFunc, consumer, TimeUnit.MILLISECONDS)
            .tags(tags)
            .description(desc)
            .register(registry);
    }

    private void bindTimeGauge(MeterRegistry registry, String name, String desc,
            ToDoubleFunction<StatisticsHandler> consumer) {
        TimeGauge.builder(name, statisticsHandler, TimeUnit.MILLISECONDS, consumer)
            .tags(tags)
            .description(desc)
            .register(registry);
    }

    private void bindCounter(MeterRegistry registry, String name, String desc,
            ToDoubleFunction<StatisticsHandler> consumer) {
        FunctionCounter.builder(name, statisticsHandler, consumer).tags(tags).description(desc).register(registry);
    }

    private void buildStatusCounter(MeterRegistry registry, String status,
            ToDoubleFunction<StatisticsHandler> consumer) {
        FunctionCounter.builder("jetty.responses", statisticsHandler, consumer)
            .tags(tags)
            .description("Number of requests with response status")
            .tags("status", status)
            .register(registry);
    }

}
