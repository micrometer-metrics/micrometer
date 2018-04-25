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

import static io.micrometer.dynatrace.DynatraceMetricDefinition.DynatraceUnit;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
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
    private final URL customDeviceMetricEndpoint;

    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;

        this.config().namingConvention(new DynatraceNamingConvention());

        try {
            this.customMetricEndpointTemplate = URI.create(config.uri() + "/api/v1/timeseries/").toURL();
        } catch (MalformedURLException e) {
            // not possible
            throw new RuntimeException(e);
        }

        try {
            this.customDeviceMetricEndpoint = URI.create(config.uri() +
                "/api/v1/entity/infrastructure/custom/" + config.deviceId() + "?api-token=" + config.apiToken())
                .toURL();
        } catch (MalformedURLException e) {
            // not possible
            throw new RuntimeException(e);
        }

        if (config.enabled())
            start(threadFactory);
    }

    @Override
    protected void publish() {
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
                postCustomMetricValues(series.stream()
                    .map(DynatraceCustomMetric::getTimeSeries)
                    .filter(this::isCustomMetricCreated)
                    .collect(Collectors.toList()));
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

    private DynatraceCustomMetric createCustomMetric(final Meter.Id id, final long time, final Number value) {
        return createCustomMetric(id, time, value, DynatraceUnit.fromPlural(id.getBaseUnit()));
    }

    private DynatraceCustomMetric createCustomMetric(final Meter.Id id, final long time, final Number value, final DynatraceUnit unit) {
        final String metricId = getConventionName(id);
        final List<Tag> tags = getConventionTags(id);
        return new DynatraceCustomMetric(
            new DynatraceMetricDefinition(metricId, id.getDescription(), unit, extractDimensions(tags), config.technologyTypes()),
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

            executeHttpCall(customMetricEndpoint, "PUT", customMetric.asJson(),
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

    private void postCustomMetricValues(final List<DynatraceTimeSeries> timeSeries) {
        executeHttpCall(customDeviceMetricEndpoint, "POST",
            timeSeries.stream()
                .map(DynatraceTimeSeries::asJson)
                .collect(joining(",")) +
                "]}",
            status -> logger.info("successfully sent {} timeSeries to Dynatrace", timeSeries.size()),
            (status, errorBody) -> logger.error("failed to send timeSeries, status: {} body: {}", status, errorBody));
    }

    private void executeHttpCall(final URL url,
                                 final String method,
                                 final String body,
                                 final Consumer<Integer> successHandler,
                                 final BiConsumer<Integer, String> errorHandler) {
        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout((int) config.connectTimeout().toMillis());
            con.setReadTimeout((int) config.readTimeout().toMillis());
            con.setRequestMethod(method);
            con.setRequestProperty("Content-Type", "application/json");

            con.setDoOutput(true);

            try (final OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            final int status = con.getResponseCode();

            if (status >= 200 && status < 300) {
                successHandler.accept(status);
            } else  {
                String errorBody;
                try (InputStream in = con.getErrorStream()) {
                    errorBody = new BufferedReader(new InputStreamReader(in))
                        .lines().collect(joining("\n"));
                }
                errorHandler.accept(status, errorBody);
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

    private Meter.Id idWithSuffix(final Meter.Id id, final String suffix) {
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

