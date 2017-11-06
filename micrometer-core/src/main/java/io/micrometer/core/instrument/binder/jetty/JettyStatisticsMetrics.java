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

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;


import org.eclipse.jetty.server.handler.StatisticsHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public class JettyStatisticsMetrics implements MeterBinder {
    private final StatisticsHandler statisticsHandler;
    private static final List<Tag> EMPTY = new ArrayList<>();

    public JettyStatisticsMetrics(StatisticsHandler statisticsHandler) {
        this.statisticsHandler = statisticsHandler;
    }

    @Override
    public void bindTo(MeterRegistry reg) {
        bindTimer(reg,"jetty.requests", "Request duration", StatisticsHandler::getRequests, StatisticsHandler::getRequestTimeTotal);
        bindTimer(reg,"jetty.dispatched", "Dispatch duration", StatisticsHandler::getDispatched, StatisticsHandler::getDispatchedTimeTotal);

        bindCounter(reg,"jetty.async.requests", "Total number of async requests", StatisticsHandler::getAsyncRequests);
        bindCounter(reg,"jetty.async.dispatches", "Total number of requested that have been asynchronously dispatched", StatisticsHandler::getAsyncDispatches);
        bindCounter(reg,"jetty.async.expires", "Total number of async requests requests that have expired", StatisticsHandler::getExpires);
        FunctionCounter.builder("jetty.responses", statisticsHandler, StatisticsHandler::getResponsesBytesTotal)
            .description("Total number of bytes across all responses")
            .baseUnit("bytes")
            .register(reg);


        bindGauge(reg,"jetty.requests.active", "Number of requests currently active", StatisticsHandler::getRequestsActive);
        bindGauge(reg,"jetty.dispatched.active", "Number of dispatches currently active", StatisticsHandler::getDispatchedActive);
        bindGauge(reg,"jetty.dispatched.active.max", "Maximum number of active dispatches being handled", StatisticsHandler::getDispatchedActiveMax);
        bindGauge(reg,"jetty.dispatched.time.max", "Maximum time spent in dispatch handling", StatisticsHandler::getDispatchedTimeMax);
        bindGauge(reg,"jetty.async.requests.waiting", "Currently waiting async requests", StatisticsHandler::getAsyncRequestsWaiting);
        bindGauge(reg,"jetty.async.requests.waiting.max", "Maximum number of waiting async requests", StatisticsHandler::getAsyncRequestsWaitingMax);

        bindTimeGauge(reg,"jetty.request.time.max", "Maximum time spent handling requests", StatisticsHandler::getRequestTimeMax);
        bindTimeGauge(reg,"jetty.stats", "Time in seconds stats have been collected for", StatisticsHandler::getStatsOnMs);

        bindStatusCounters(reg);
    }

    private void bindStatusCounters(MeterRegistry reg) {
        buildStatusCounter(reg,"1xx", StatisticsHandler::getResponses1xx);
        buildStatusCounter(reg,"2xx", StatisticsHandler::getResponses2xx);
        buildStatusCounter(reg,"3xx", StatisticsHandler::getResponses3xx);
        buildStatusCounter(reg,"4xx", StatisticsHandler::getResponses4xx);
        buildStatusCounter(reg,"5xx", StatisticsHandler::getResponses5xx);
    }

    private void bindGauge(MeterRegistry reg, String name, String desc, ToDoubleFunction<StatisticsHandler> func) {
        Gauge.builder(name, statisticsHandler, func)
            .description(desc)
            .register(reg);
    }

    private void bindTimer(MeterRegistry reg, String name, String desc, ToLongFunction<StatisticsHandler> countFunc, ToDoubleFunction<StatisticsHandler> consumer) {
        FunctionTimer.builder(name, statisticsHandler, countFunc, consumer, TimeUnit.MILLISECONDS)
            .description(desc)
            .register(reg);
    }

    private void bindTimeGauge(MeterRegistry reg, String name, String desc, ToDoubleFunction<StatisticsHandler> consumer) {
        TimeGauge.builder(name, statisticsHandler, TimeUnit.MILLISECONDS, consumer)
            .description(desc)
            .register(reg);
    }

    private void bindCounter(MeterRegistry reg, String name, String desc, ToDoubleFunction<StatisticsHandler> consumer) {
        FunctionCounter.builder(name, statisticsHandler, consumer)
            .description(desc)
            .register(reg);
    }

    private void buildStatusCounter(MeterRegistry reg, String status, ToDoubleFunction<StatisticsHandler> consumer) {
        FunctionCounter.builder("jetty.responses", statisticsHandler, consumer)
            .description("Number of requests with response status")
            .tags("status", status)
            .register(reg);
    }


}
