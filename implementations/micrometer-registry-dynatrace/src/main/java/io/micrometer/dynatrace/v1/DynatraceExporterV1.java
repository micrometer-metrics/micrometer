/*
 * Copyright 2017-2021 VMware, Inc.
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
package io.micrometer.dynatrace.v1;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.dynatrace.AbstractDynatraceExporter;
import io.micrometer.dynatrace.DynatraceApiVersion;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.DynatraceNamingConvention;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.micrometer.dynatrace.v1.DynatraceMetricDefinition.DynatraceUnit;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Implementation for Dynatrace v1 metrics API export.
 *
 * @author Oriol Barcelona
 * @author Jon Schneider
 * @author Johnny Lim
 * @author PJ Fanning
 * @author Georg Pirklbauer
 * @since 1.8.0
 */
public class DynatraceExporterV1 extends AbstractDynatraceExporter {

    // max message size in bytes that Dynatrace will accept
    private static final int MAX_MESSAGE_SIZE = 15360;

    private final InternalLogger logger = InternalLoggerFactory.getInstance(DynatraceExporterV1.class);

    /**
     * Metric names for which we have created the custom metric in the API
     */
    private final Set<String> createdCustomMetrics = ConcurrentHashMap.newKeySet();

    private final String customMetricEndpointTemplate;

    private final NamingConvention namingConvention;

    private static final String AUTHORIZATION_HEADER_KEY = "Authorization";

    private static final String AUTHORIZATION_HEADER_VALUE_TEMPLATE = "Api-Token %s";

    public DynatraceExporterV1(DynatraceConfig config, Clock clock, HttpSender httpClient) {
        super(config, clock, httpClient);

        this.customMetricEndpointTemplate = config.uri() + "/api/v1/timeseries/";
        this.namingConvention = new DynatraceNamingConvention(NamingConvention.dot, DynatraceApiVersion.V1);
    }

    @Override
    public void export(List<Meter> meters) {
        String customDeviceMetricEndpoint = config.uri() + "/api/v1/entity/infrastructure/custom/" + config.deviceId();

        for (List<Meter> batch : new MeterPartition(meters, config.batchSize())) {
            final List<DynatraceCustomMetric> series = batch.stream()
                .flatMap(meter -> meter.match(this::writeMeter, this::writeMeter, this::writeTimer, this::writeSummary,
                        this::writeLongTaskTimer, this::writeMeter, this::writeMeter, this::writeFunctionTimer,
                        this::writeMeter))
                .collect(Collectors.toList());

            // TODO is there a way to batch submissions of multiple metrics?
            series.stream()
                .map(DynatraceCustomMetric::getMetricDefinition)
                .filter(this::isCustomMetricNotCreated)
                .forEach(this::putCustomMetric);

            if (!createdCustomMetrics.isEmpty() && !series.isEmpty()) {
                postCustomMetricValues(config.technologyType(), config.group(),
                        series.stream()
                            .map(DynatraceCustomMetric::getTimeSeries)
                            .filter(this::isCustomMetricCreated)
                            .collect(Collectors.toList()),
                        customDeviceMetricEndpoint);
            }
        }
    }

    // VisibleForTesting
    Stream<DynatraceCustomMetric> writeMeter(Meter meter) {
        final long wallTime = clock.wallTime();
        return StreamSupport.stream(meter.measure().spliterator(), false)
            .map(Measurement::getValue)
            .filter(Double::isFinite)
            .map(value -> createCustomMetric(meter.getId(), wallTime, value));
    }

    private Stream<DynatraceCustomMetric> writeLongTaskTimer(LongTaskTimer longTaskTimer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = longTaskTimer.getId();
        return Stream.of(
                createCustomMetric(idWithSuffix(id, "activeTasks"), wallTime, longTaskTimer.activeTasks(),
                        DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, longTaskTimer.duration(getBaseTimeUnit())));
    }

    // VisibleForTesting
    Stream<DynatraceCustomMetric> writeSummary(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = summary.getId();
        final HistogramSnapshot snapshot = summary.takeSnapshot();

        return Stream.of(createCustomMetric(idWithSuffix(id, "sum"), wallTime, snapshot.total()),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, snapshot.mean()),
                createCustomMetric(idWithSuffix(id, "max"), wallTime, snapshot.max()));
    }

    private Stream<DynatraceCustomMetric> writeFunctionTimer(FunctionTimer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();

        return Stream.of(createCustomMetric(idWithSuffix(id, "count"), wallTime, timer.count(), DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, timer.mean(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "sum"), wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    private Stream<DynatraceCustomMetric> writeTimer(Timer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();
        final HistogramSnapshot snapshot = timer.takeSnapshot();

        return Stream.of(createCustomMetric(idWithSuffix(id, "sum"), wallTime, snapshot.total(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, snapshot.mean(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "max"), wallTime, snapshot.max(getBaseTimeUnit())));
    }

    private DynatraceCustomMetric createCustomMetric(Meter.Id id, long time, Number value) {
        return createCustomMetric(id, time, value, DynatraceUnit.fromPlural(id.getBaseUnit()));
    }

    private DynatraceCustomMetric createCustomMetric(Meter.Id id, long time, Number value,
            @Nullable DynatraceUnit unit) {
        final String metricId = getConventionName(id);
        final List<Tag> tags = getConventionTags(id);
        return new DynatraceCustomMetric(
                new DynatraceMetricDefinition(metricId, id.getDescription(), unit, extractDimensions(tags),
                        new String[] { config.technologyType() }, config.group()),
                new DynatraceTimeSeries(metricId, time, value.doubleValue(), extractDimensionValues(tags)));
    }

    private List<Tag> getConventionTags(Meter.Id id) {
        return id.getConventionTags(namingConvention);
    }

    private String getConventionName(Meter.Id id) {
        return id.getConventionName(namingConvention);
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
        HttpSender.Request.Builder requestBuilder;
        try {
            requestBuilder = httpClient.put(customMetricEndpointTemplate + customMetric.getMetricId())
                .withHeader(AUTHORIZATION_HEADER_KEY,
                        String.format(AUTHORIZATION_HEADER_VALUE_TEMPLATE, config.apiToken()))
                .withJsonContent(customMetric.asJson());
        }
        catch (Exception ex) {
            if (logger.isErrorEnabled()) {
                logger.error("failed to build request", ex);
            }
            return; // don't try to export data points, the request can't be built
        }

        HttpSender.Response httpResponse = trySendHttpRequest(requestBuilder);

        if (httpResponse != null) {
            httpResponse.onSuccess(response -> {
                logger.debug("created {} as custom metric in Dynatrace", customMetric.getMetricId());
                createdCustomMetrics.add(customMetric.getMetricId());
            })
                .onError(response -> logger.error(
                        "failed to create custom metric {} in Dynatrace: Error Code={}, Response Body={}",
                        customMetric.getMetricId(), response.code(), response.body()));
        }
    }

    private void postCustomMetricValues(String type, String group, List<DynatraceTimeSeries> timeSeries,
            String customDeviceMetricEndpoint) {
        for (DynatraceBatchedPayload postMessage : createPostMessages(type, group, timeSeries)) {
            HttpSender.Request.Builder requestBuilder;
            try {
                requestBuilder = httpClient.post(customDeviceMetricEndpoint)
                    .withJsonContent(postMessage.payload)
                    .withHeader(AUTHORIZATION_HEADER_KEY,
                            String.format(AUTHORIZATION_HEADER_VALUE_TEMPLATE, config.apiToken()));
            }
            catch (Exception ex) {
                if (logger.isErrorEnabled()) {
                    logger.error("failed to build request", ex);
                }

                return; // don't try to export data points, the request can't be built
            }

            HttpSender.Response httpResponse = trySendHttpRequest(requestBuilder);

            if (httpResponse != null) {
                httpResponse.onSuccess(response -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("successfully sent {} metrics to Dynatrace ({} bytes).", postMessage.metricCount,
                                postMessage.payload.getBytes(UTF_8).length);
                    }
                }).onError(response -> {
                    logger.error("failed to send metrics to Dynatrace: Error Code={}, Response Body={}",
                            response.code(), response.body());
                    logger.debug("failed metrics payload: {}", postMessage.payload);
                });
            }
        }
    }

    // VisibleForTesting
    HttpSender.Response trySendHttpRequest(HttpSender.Request.Builder requestBuilder) {
        try {
            return requestBuilder.send();
        }
        catch (Throwable e) {
            if (logger.isErrorEnabled()) {
                logger.error("failed to send metrics to Dynatrace", e);
            }
            return null;
        }
    }

    // VisibleForTesting
    List<DynatraceBatchedPayload> createPostMessages(String type, String group, List<DynatraceTimeSeries> timeSeries) {
        final String header = "{\"type\":\"" + type + '\"'
                + (StringUtils.isNotBlank(group) ? ",\"group\":\"" + group + '\"' : "") + ",\"series\":[";
        final String footer = "]}";
        final int headerFooterBytes = header.getBytes(UTF_8).length + footer.getBytes(UTF_8).length;
        final int maxMessageSize = MAX_MESSAGE_SIZE - headerFooterBytes;
        List<DynatraceBatchedPayload> payloadBodies = createPostMessageBodies(timeSeries, maxMessageSize);
        return payloadBodies.stream().map(body -> {
            String message = header + body.payload + footer;
            return new DynatraceBatchedPayload(message, body.metricCount);
        }).collect(Collectors.toList());
    }

    private List<DynatraceBatchedPayload> createPostMessageBodies(List<DynatraceTimeSeries> timeSeries, long maxSize) {
        ArrayList<DynatraceBatchedPayload> messages = new ArrayList<>();
        StringBuilder payload = new StringBuilder();
        int metricCount = 0;
        long totalByteCount = 0;
        for (DynatraceTimeSeries ts : timeSeries) {
            String json = ts.asJson();
            int jsonByteCount = json.getBytes(UTF_8).length;
            if (jsonByteCount > maxSize) {
                logger.debug("Time series data for metric '{}' is too large ({} bytes) to send to Dynatrace.",
                        ts.getMetricId(), jsonByteCount);
                continue;
            }
            if ((payload.length() == 0 && totalByteCount + jsonByteCount > maxSize)
                    || (payload.length() > 0 && totalByteCount + jsonByteCount + 1 > maxSize)) {
                messages.add(new DynatraceBatchedPayload(payload.toString(), metricCount));
                payload.setLength(0);
                totalByteCount = 0;
                metricCount = 0;
            }
            if (payload.length() > 0) {
                payload.append(',');
                totalByteCount++;
            }
            payload.append(json);
            totalByteCount += jsonByteCount;
            metricCount++;
        }
        if (payload.length() > 0) {
            messages.add(new DynatraceBatchedPayload(payload.toString(), metricCount));
        }
        return messages;
    }

    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

}
