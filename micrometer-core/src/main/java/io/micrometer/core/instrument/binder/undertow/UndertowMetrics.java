/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.undertow;

import com.google.common.base.CaseFormat;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.undertow.server.handlers.MetricsHandler;
import io.undertow.servlet.api.MetricsCollector;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import static io.undertow.server.handlers.MetricsHandler.MetricResult;

/**
 * {@link MetricsHandler} to collect metrics for {@link io.undertow.Undertow}
 *
 * @author Dharmesh Jogadia
 * */
@NonNullApi
@NonNullFields
public class UndertowMetrics implements MetricsCollector, MeterBinder {

    private static final String METRIC_PREFIX = "undertow.";
    private Map<String, MetricsHandler> metricsHandlers;
    private Iterable<Tag> tags;

    public UndertowMetrics(Iterable<Tag> tags) {
        metricsHandlers = new HashMap<>();
        this.tags = tags;
    }

    @Override
    public void registerMetric(String servletName, MetricsHandler handler) {
        metricsHandlers.put(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, servletName), handler);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (Map.Entry<String, MetricsHandler> handlerEntry : metricsHandlers.entrySet()) {
            MetricResult metricResult = handlerEntry.getValue().getMetrics();
            bindTimer(registry, METRIC_PREFIX + handlerEntry.getKey() + "." + "requests", "Request duration", metricResult, MetricResult::getTotalRequests, MetricsHandler.MetricResult::getMinRequestTime);
            bindTimeGauge(registry, METRIC_PREFIX + handlerEntry.getKey() + ".request.time.max", "Maximum time spent handling requests", metricResult, MetricResult::getMaxRequestTime);
            bindTimeGauge(registry, METRIC_PREFIX + handlerEntry.getKey() + ".request.time.min", "Minimum time spent handling requests", metricResult, MetricResult::getMinRequestTime);
            bindCounter(registry, METRIC_PREFIX + handlerEntry.getKey() + ".request.errors", "Total number of error requests ", metricResult, MetricResult::getTotalErrors);
        }
    }

    private void bindTimer(MeterRegistry registry, String name, String desc, MetricsHandler.MetricResult metricResult, ToLongFunction<MetricsHandler.MetricResult> countFunc, ToDoubleFunction<MetricsHandler.MetricResult> consumer) {
        FunctionTimer.builder(name, metricResult, countFunc, consumer, TimeUnit.MILLISECONDS)
                .tags(tags)
                .description(desc)
                .register(registry);
    }

    private void bindTimeGauge(MeterRegistry registry, String name, String desc, MetricResult metricResult, ToDoubleFunction<MetricResult> consumer) {
        TimeGauge.builder(name, metricResult, TimeUnit.MILLISECONDS, consumer)
                .tags(tags)
                .description(desc)
                .register(registry);
    }

    private void bindCounter(MeterRegistry registry, String name, String desc, MetricResult metricResult, ToDoubleFunction<MetricResult> consumer) {
        FunctionCounter.builder(name, metricResult, consumer)
                .tags(tags)
                .description(desc)
                .register(registry);
    }
}
