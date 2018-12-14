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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.micrometer.dynatrace.DynatraceMetricDefinition.DynatraceUnit;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 * {@link StepMeterRegistry} for Dynatrace.
 *
 * @author Oriol Barcelona
 * @author Jon Schneider
 * @author Johnny Lim
 * @since 1.1.0
 */
public class DynatraceMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("dynatrace-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(DynatraceMeterRegistry.class);
    private final DynatraceConfig config;
    private final HttpSender httpClient;

    /**
     * Metric names for which we have created the custom metric in the API
     */
    private final Set<String> createdCustomMetrics = ConcurrentHashMap.newKeySet();
    private final String customMetricEndpointTemplate;

    @SuppressWarnings("deprecation")
    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private DynatraceMeterRegistry(DynatraceConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        requireNonNull(config.uri());
        requireNonNull(config.deviceId());
        requireNonNull(config.apiToken());

        this.config = config;
        this.httpClient = httpClient;

        config().namingConvention(new DynatraceNamingConvention());

        this.customMetricEndpointTemplate = config.uri() + "/api/v1/timeseries/";

        start(threadFactory);
    }

    public static Builder builder(DynatraceConfig config) {
        return new Builder(config);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to dynatrace every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        String customDeviceMetricEndpoint = config.uri() + "/api/v1/entity/infrastructure/custom/" +
                config.deviceId() + "?api-token=" + config.apiToken();

        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            final List<DynatraceCustomMetric> series = batch.stream()
                    .flatMap(meter -> meter.match(
                            this::writeMeter,
                            this::writeMeter,
                            this::writeTimer,
                            this::writeSummary,
                            this::writeLongTaskTimer,
                            this::writeMeter,
                            this::writeMeter,
                            this::writeFunctionTimer,
                            this::writeMeter)
                    )
                    .collect(Collectors.toList());

            // TODO is there a way to batch submissions of multiple metrics?
            series.stream()
                    .map(DynatraceCustomMetric::getMetricDefinition)
                    .filter(this::isCustomMetricNotCreated)
                    .forEach(this::putCustomMetric);

            if (!createdCustomMetrics.isEmpty() && !series.isEmpty()) {
                postCustomMetricValues(
                        config.technologyType(),
                        series.stream()
                                .map(DynatraceCustomMetric::getTimeSeries)
                                .filter(this::isCustomMetricCreated)
                                .collect(Collectors.toList()),
                        customDeviceMetricEndpoint);
            }
        }
    }

    private Stream<DynatraceCustomMetric> writeMeter(Meter meter) {
        final long wallTime = clock.wallTime();
        return StreamSupport.stream(meter.measure().spliterator(), false)
                .map(ms -> createCustomMetric(meter.getId(), wallTime, ms.getValue()));
    }

    private Stream<DynatraceCustomMetric> writeLongTaskTimer(LongTaskTimer longTaskTimer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = longTaskTimer.getId();
        return Stream.of(
                createCustomMetric(idWithSuffix(id, "activeTasks"), wallTime, longTaskTimer.activeTasks(), DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, longTaskTimer.duration(getBaseTimeUnit())));
    }

    private Stream<DynatraceCustomMetric> writeSummary(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = summary.getId();
        final HistogramSnapshot snapshot = summary.takeSnapshot();

        return Stream.of(
                createCustomMetric(idWithSuffix(id, "sum"), wallTime, snapshot.total(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, snapshot.mean(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "max"), wallTime, snapshot.max(getBaseTimeUnit())));
    }

    private Stream<DynatraceCustomMetric> writeFunctionTimer(FunctionTimer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();

        return Stream.of(
                createCustomMetric(idWithSuffix(id, "count"), wallTime, timer.count(), DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, timer.mean(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "sum"), wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    private Stream<DynatraceCustomMetric> writeTimer(Timer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();
        final HistogramSnapshot snapshot = timer.takeSnapshot();

        return Stream.of(
                createCustomMetric(idWithSuffix(id, "sum"), wallTime, snapshot.total(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, snapshot.mean(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "max"), wallTime, snapshot.max(getBaseTimeUnit())));
    }

    private DynatraceCustomMetric createCustomMetric(Meter.Id id, long time, Number value) {
        return createCustomMetric(id, time, value, DynatraceUnit.fromPlural(id.getBaseUnit()));
    }

    private DynatraceCustomMetric createCustomMetric(Meter.Id id, long time, Number value, @Nullable DynatraceUnit unit) {
        final String metricId = getConventionName(id);
        final List<Tag> tags = getConventionTags(id);
        return new DynatraceCustomMetric(
                new DynatraceMetricDefinition(metricId, id.getDescription(), unit, extractDimensions(tags), new String[]{config.technologyType()}),
                new DynatraceTimeSeries(metricId, time, value.doubleValue(), extractDimensionValues(tags)));
    }

    private Set<String> extractDimensions(List<Tag> tags) {
        return tags.stream().map(Tag::getKey).collect(Collectors.toSet());
    }

    private Map<String, String> extractDimensionValues(List<Tag> tags) {
        return tags.stream().collect(Collectors.toMap(Tag::getKey, Tag::getValue));
    }

    private boolean isCustomMetricNotCreated(final DynatraceMetricDefinition metric) {
        return !createdCustomMetrics.contains(metric.getMetricId());
    }

    private boolean isCustomMetricCreated(final DynatraceTimeSeries timeSeries) {
        return createdCustomMetrics.contains(timeSeries.getMetricId());
    }

    // VisibleForTesting
    void putCustomMetric(final DynatraceMetricDefinition customMetric) {
        try {
            httpClient.put(customMetricEndpointTemplate + customMetric.getMetricId() + "?api-token=" + config.apiToken())
                    .withJsonContent(customMetric.asJson())
                    .send()
                    .onSuccess(response -> {
                        logger.debug("created {} as custom metric in dynatrace", customMetric.getMetricId());
                        createdCustomMetrics.add(customMetric.getMetricId());
                    })
                    .onError(response -> {
                        if (logger.isErrorEnabled()) {
                            logger.error("failed to create custom metric {} in dynatrace: {}", customMetric.getMetricId(),
                                    response.body());
                        }
                    });
        } catch (Throwable e) {
            logger.error("failed to create custom metric in dynatrace: {}", customMetric.getMetricId(), e);
        }
    }

    private void postCustomMetricValues(String type, List<DynatraceTimeSeries> timeSeries, String customDeviceMetricEndpoint) {
        try {
            httpClient.post(customDeviceMetricEndpoint)
                    .withJsonContent(createPostMessage(type, timeSeries))
                    .send()
                    .onSuccess(response -> logger.debug("successfully sent {} metrics to Dynatrace.", timeSeries.size()))
                    .onError(response -> logger.error("failed to send metrics to dynatrace: {}", response.body()));
        } catch (Throwable e) {
            logger.error("failed to send metrics to dynatrace", e);
        }
    }

    private String createPostMessage(String type, List<DynatraceTimeSeries> timeSeries) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(type).append('\"')
            .append(",\"series\":[")
            .append(timeSeries.stream()
                    .map(DynatraceTimeSeries::asJson)
                    .collect(joining(",")))
            .append("]}");
        String message = sb.toString();
        logger.debug("created post message:\n{}", message);
        return message;
    }

    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {
        private final DynatraceConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(DynatraceConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpSender httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public DynatraceMeterRegistry build() {
            return new DynatraceMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }

    class DynatraceCustomMetric {
        private final DynatraceMetricDefinition metricDefinition;
        private final DynatraceTimeSeries timeSeries;

        DynatraceCustomMetric(final DynatraceMetricDefinition metricDefinition, final DynatraceTimeSeries timeSeries) {
            this.metricDefinition = metricDefinition;
            this.timeSeries = timeSeries;
        }

        DynatraceMetricDefinition getMetricDefinition() {
            return metricDefinition;
        }

        DynatraceTimeSeries getTimeSeries() {
            return timeSeries;
        }
    }
}

