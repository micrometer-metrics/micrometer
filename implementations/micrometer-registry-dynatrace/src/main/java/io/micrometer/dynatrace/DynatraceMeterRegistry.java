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
import io.micrometer.core.instrument.util.*;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.micrometer.dynatrace.DynatraceMetricDefinition.DynatraceUnit;
import static java.util.stream.Collectors.joining;

/**
 * @author Oriol Barcelona
 */
public class DynatraceMeterRegistry extends StepMeterRegistry {

    private final Logger logger = LoggerFactory.getLogger(DynatraceMeterRegistry.class);
    private final DynatraceConfig config;

    /**
     * Metric names for which we have created the custom metric in the API
     */
    private final Set<String> createdCustomMetrics = ConcurrentHashMap.newKeySet();
    private final URL customMetricEndpointTemplate;

    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;

        this.config().namingConvention(new DynatraceNamingConvention());

        this.customMetricEndpointTemplate = URIUtils.toURL(config.uri() + "/api/v1/timeseries/");

        if (config.enabled())
            start(threadFactory);
    }

    @Override
    protected void publish() {
        URL customDeviceMetricEndpoint = URIUtils.toURL(config.uri() +
                "/api/v1/entity/infrastructure/custom/" + config.deviceId() + "?api-token=" + config.apiToken());

        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            final List<DynatraceCustomMetric> series = batch.stream()
                    .flatMap(meter -> {
                        if (meter instanceof Timer) {
                            return createCustomMetric((Timer) meter);
                        } else if (meter instanceof FunctionTimer) {
                            return createCustomMetric((FunctionTimer) meter);
                        } else if (meter instanceof DistributionSummary) {
                            return createCustomMetric((DistributionSummary) meter);
                        } else if (meter instanceof LongTaskTimer) {
                            return createCustomMetric((LongTaskTimer) meter);
                        } else {
                            return createCustomMetric(meter);
                        }
                    })
                    .collect(Collectors.toList());

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

    private Stream<DynatraceCustomMetric> createCustomMetric(final Meter meter) {
        final long wallTime = clock.wallTime();
        return StreamSupport.stream(meter.measure().spliterator(), false)
                .map(ms -> createCustomMetric(meter.getId(), wallTime, ms.getValue()));
    }

    private Stream<DynatraceCustomMetric> createCustomMetric(final LongTaskTimer longTaskTimer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = longTaskTimer.getId();
        return Stream.of(
                createCustomMetric(idWithSuffix(id, "activeTasks"), wallTime, longTaskTimer.activeTasks(), DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, longTaskTimer.duration(getBaseTimeUnit())));
    }

    private Stream<DynatraceCustomMetric> createCustomMetric(final DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = summary.getId();
        final HistogramSnapshot snapshot = summary.takeSnapshot();

        return Stream.of(
                createCustomMetric(idWithSuffix(id, "sum"), wallTime, snapshot.total(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, snapshot.mean(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "max"), wallTime, snapshot.max(getBaseTimeUnit())));
    }

    private Stream<DynatraceCustomMetric> createCustomMetric(final FunctionTimer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();

        return Stream.of(
                createCustomMetric(idWithSuffix(id, "count"), wallTime, timer.count(), DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, timer.mean(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "sum"), wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    private Stream<DynatraceCustomMetric> createCustomMetric(final Timer timer) {
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

    private void putCustomMetric(final DynatraceMetricDefinition customMetric) {
        try {
            final URL customMetricEndpoint = new URL(customMetricEndpointTemplate +
                    customMetric.getMetricId() + "?api-token=" + config.apiToken());

            executeHttpCall(customMetricEndpoint, HttpMethod.PUT, customMetric.asJson(),
                    status -> {
                        logger.debug("created '{}' as custom metric", customMetric.getMetricId());
                        createdCustomMetrics.add(customMetric.getMetricId());
                    },
                    (status, errorMsg) -> logger.error("failed to create custom metric '{}', status: {} message: {}",
                            customMetric.getMetricId(), status, errorMsg));
        } catch (final MalformedURLException e) {
            logger.warn("Failed to compose URL for custom metric '{}'", customMetric.getMetricId());
        }
    }

    private void postCustomMetricValues(String type, List<DynatraceTimeSeries> timeSeries, URL customDeviceMetricEndpoint) {
        executeHttpCall(customDeviceMetricEndpoint, HttpMethod.POST,
                "{\"type\":\"" + type + "\"" +
                        ",\"series\":[" +
                        timeSeries.stream()
                                .map(DynatraceTimeSeries::asJson)
                                .collect(joining(",")) +
                        "]}",
                status -> logger.info("successfully sent {} timeSeries to Dynatrace", timeSeries.size()),
                (status, errorBody) -> logger.error("failed to send timeSeries, status: {} body: {}", status, errorBody));
    }

    private void executeHttpCall(URL url,
                                 String method,
                                 String body,
                                 Consumer<Integer> successHandler,
                                 BiConsumer<Integer, String> errorHandler) {
        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout((int) config.connectTimeout().toMillis());
            con.setReadTimeout((int) config.readTimeout().toMillis());
            con.setRequestMethod(method);
            con.setRequestProperty(HttpHeader.CONTENT_TYPE, MediaType.APPLICATION_JSON);

            con.setDoOutput(true);

            try (final OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            final int status = con.getResponseCode();

            if (status >= 200 && status < 300) {
                successHandler.accept(status);
            } else {
                errorHandler.accept(status, IOUtils.toString(con.getErrorStream()));
            }
        } catch (final Throwable e) {
            logger.warn("failed to execute http call to '{}' using method '{}'", url, method, e);
        } finally {
            try {
                if (con != null) {
                    con.disconnect();
                }
            } catch (Exception ignore) {
            }
        }
    }

    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
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

