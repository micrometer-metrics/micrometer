package io.micrometer.dynatrace.v1;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.lang.Nullable;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.DynatraceMeterRegistryImplBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DynatraceMeterRegistryImplV1 extends DynatraceMeterRegistryImplBase {
    private static final int MAX_MESSAGE_SIZE = 15360; //max message size in bytes that Dynatrace will accept
    private final Logger logger = LoggerFactory.getLogger(DynatraceMeterRegistryImplV1.class.getName());


    /**
     * Metric names for which we have created the custom metric in the API
     */
    private final Set<String> createdCustomMetrics = ConcurrentHashMap.newKeySet();
    private final String customMetricEndpointTemplate;

    private final NamingConvention namingConvention;


    public DynatraceMeterRegistryImplV1(DynatraceConfig config, Clock clock, HttpSender httpClient) {
        this(config, clock, DEFAULT_THREAD_FACTORY, httpClient);
    }

    public DynatraceMeterRegistryImplV1(DynatraceConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock, threadFactory, httpClient);

        this.config = config;
        this.httpClient = httpClient;
        this.customMetricEndpointTemplate = config.uri() + "/api/v1/timeseries/";
        this.namingConvention = new DynatraceNamingConvention();
    }

    @Override
    public void publish(MeterRegistry registry) {
        String customDeviceMetricEndpoint = config.uri() + "/api/v1/entity/infrastructure/custom/" +
                config.deviceId() + "?api-token=" + config.apiToken();

        for (List<Meter> batch : MeterPartition.partition(registry, config.batchSize())) {
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
                        config.group(),
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
                createCustomMetric(idWithSuffix(id, "activeTasks"), wallTime, longTaskTimer.activeTasks(), DynatraceMetricDefinition.DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, longTaskTimer.duration(getBaseTimeUnit())));
    }

    private Stream<DynatraceCustomMetric> writeSummary(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = summary.getId();
        final HistogramSnapshot snapshot = summary.takeSnapshot();

        return Stream.of(
                createCustomMetric(idWithSuffix(id, "sum"), wallTime, snapshot.total(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceMetricDefinition.DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, snapshot.mean(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "max"), wallTime, snapshot.max(getBaseTimeUnit())));
    }

    private Stream<DynatraceCustomMetric> writeFunctionTimer(FunctionTimer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();

        return Stream.of(
                createCustomMetric(idWithSuffix(id, "count"), wallTime, timer.count(), DynatraceMetricDefinition.DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, timer.mean(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "sum"), wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    private Stream<DynatraceCustomMetric> writeTimer(Timer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();
        final HistogramSnapshot snapshot = timer.takeSnapshot();

        return Stream.of(
                createCustomMetric(idWithSuffix(id, "sum"), wallTime, snapshot.total(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceMetricDefinition.DynatraceUnit.Count),
                createCustomMetric(idWithSuffix(id, "avg"), wallTime, snapshot.mean(getBaseTimeUnit())),
                createCustomMetric(idWithSuffix(id, "max"), wallTime, snapshot.max(getBaseTimeUnit())));
    }

    private DynatraceCustomMetric createCustomMetric(Meter.Id id, long time, Number value) {
        return createCustomMetric(id, time, value, DynatraceMetricDefinition.DynatraceUnit.fromPlural(id.getBaseUnit()));
    }

    private DynatraceCustomMetric createCustomMetric(Meter.Id id, long time, Number value, @Nullable DynatraceMetricDefinition.DynatraceUnit unit) {
        final String metricId = getConventionName(id);
        final List<Tag> tags = getConventionTags(id);
        return new DynatraceCustomMetric(
                new DynatraceMetricDefinition(metricId, id.getDescription(), unit, extractDimensions(tags), new String[]{config.technologyType()}, config.group()),
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

    private void postCustomMetricValues(String type, String group, List<DynatraceTimeSeries> timeSeries, String customDeviceMetricEndpoint) {
        try {
            for (DynatraceBatchedPayload postMessage : createPostMessages(type, group, timeSeries)) {
                httpClient.post(customDeviceMetricEndpoint)
                        .withJsonContent(postMessage.payload)
                        .send()
                        .onSuccess(response -> {
                            if (logger.isDebugEnabled()) {
                                logger.debug("successfully sent {} metrics to Dynatrace ({} bytes).",
                                        postMessage.metricCount, postMessage.payload.getBytes(UTF_8).length);
                            }
                        })
                        .onError(response -> {
                            logger.error("failed to send metrics to dynatrace: {}", response.body());
                            logger.debug("failed metrics payload: {}", postMessage.payload);
                        });
            }
        } catch (Throwable e) {
            logger.error("failed to send metrics to dynatrace", e);
        }
    }

    // VisibleForTesting
    List<DynatraceBatchedPayload> createPostMessages(String type, String group, List<DynatraceTimeSeries> timeSeries) {
        final String header = "{\"type\":\"" + type + '\"'
                + (StringUtils.isNotBlank(group) ? ",\"group\":\"" + group + '\"' : "")
                + ",\"series\":[";
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
                logger.debug("Time series data for metric '{}' is too large ({} bytes) to send to Dynatrace.", ts.getMetricId(), jsonByteCount);
                continue;
            }
            if ((payload.length() == 0 && totalByteCount + jsonByteCount > maxSize) ||
                    (payload.length() > 0 && totalByteCount + jsonByteCount + 1 > maxSize)) {
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
