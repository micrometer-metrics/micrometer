/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.dynatrace.v2;

import com.dynatrace.metric.util.DynatraceMetricApiConstants;
import com.dynatrace.metric.util.MetricException;
import com.dynatrace.metric.util.MetricLineBuilder;
import com.dynatrace.metric.util.MetricLineBuilder.MetadataStep;
import com.dynatrace.metric.util.MetricLinePreConfiguration;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.dynatrace.AbstractDynatraceExporter;
import io.micrometer.dynatrace.DynatraceConfig;
import io.micrometer.dynatrace.types.DynatraceSummarySnapshot;
import io.micrometer.dynatrace.types.DynatraceSummarySnapshotSupport;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implementation for Dynatrace v2 metrics API export.
 *
 * @author Georg Pirklbauer
 * @author Jonatan Ivanov
 * @since 1.8.0
 */
public final class DynatraceExporterV2 extends AbstractDynatraceExporter {

    private static final String METER_EXCEPTION_LOG_FORMAT = "Could not serialize meter {}: {}";

    private static final Pattern EXTRACT_LINES_OK = Pattern.compile("\"linesOk\":\\s?(\\d+)");

    private static final Pattern EXTRACT_LINES_INVALID = Pattern.compile("\"linesInvalid\":\\s?(\\d+)");

    private static final Pattern IS_NULL_ERROR_RESPONSE = Pattern.compile("\"error\":\\s?null");

    private static final Map<String, String> STATIC_DIMENSIONS = Collections.singletonMap("dt.metrics.source",
            "micrometer");

    private static final Map<String, String> UCUM_TIME_UNIT_MAP = ucumTimeUnitMap();

    // Loggers must be non-static for MockLoggerFactory.injectLogger() in tests.
    private final InternalLogger logger = InternalLoggerFactory.getInstance(DynatraceExporterV2.class);

    private MetricLinePreConfiguration preConfiguration;

    private boolean skipExport = false;

    public DynatraceExporterV2(DynatraceConfig config, Clock clock, HttpSender httpClient) {
        super(config, clock, httpClient);

        logger.info("Exporting to endpoint {}", config.uri());

        try {
            MetricLinePreConfiguration.Builder preConfigBuilder = MetricLinePreConfiguration.builder()
                .prefix(config.metricKeyPrefix())
                .defaultDimensions(enrichWithMetricsSourceDimensions(config.defaultDimensions()));

            if (config.enrichWithDynatraceMetadata()) {
                preConfigBuilder.dynatraceMetadataDimensions();
            }

            preConfiguration = preConfigBuilder.build();
        }
        catch (MetricException e) {
            // if the pre-configuration is invalid, all created metric lines would be
            // invalid, and exporting any line becomes useless. Therefore, we log an
            // error, and don't export at all.
            logger.error("Dynatrace configuration is invalid", e);
            skipExport = true;
        }
    }

    private boolean isValidEndpoint(String uri) {
        try {
            // noinspection ResultOfMethodCallIgnored
            URI.create(uri).toURL();
        }
        catch (IllegalArgumentException | MalformedURLException ex) {
            return false;
        }

        return true;
    }

    private boolean shouldIgnoreToken(DynatraceConfig config) {
        if (config.apiToken().isEmpty()) {
            return true;
        }
        if (config.uri().equals(DynatraceMetricApiConstants.getDefaultOneAgentEndpoint())) {
            logger
                .warn("Potential misconfiguration detected: Token is provided, but the endpoint is set to the local OneAgent endpoint, "
                        + "thus the token will be ignored. If exporting to the cluster API endpoint is intended, its URI has to be provided explicitly.");
            return true;
        }
        return false;
    }

    private Map<String, String> enrichWithMetricsSourceDimensions(Map<String, String> defaultDimensions) {
        LinkedHashMap<String, String> orderedDimensions = new LinkedHashMap<>(defaultDimensions);
        orderedDimensions.putAll(STATIC_DIMENSIONS);
        return orderedDimensions;
    }

    /**
     * Export to the Dynatrace v2 endpoint. Measurements that contain NaN or Infinite
     * values, as well as serialized data points that exceed length limits imposed by the
     * API will be dropped and not exported. If the number of serialized data points
     * exceeds the maximum number of allowed data points per request they will be sent in
     * chunks.
     * @param meters A list of {@link Meter Meters} that are serialized as one or more
     * metric lines.
     */
    @Override
    public void export(@NonNull List<Meter> meters) {
        if (skipExport) {
            logger.warn("Dynatrace configuration is invalid, skipping export.");
            return;
        }

        if (meters.isEmpty()) {
            logger.debug("Meter list is empty, nothing to export. Did you create any meters?");
            return;
        }

        Map<String, String> seenMetadata = null;
        if (config.exportMeterMetadata()) {
            seenMetadata = new HashMap<>();
        }

        int partitionSize = Math.min(config.batchSize(), DynatraceMetricApiConstants.getPayloadLinesLimit());
        List<String> batch = new ArrayList<>(partitionSize);

        for (Meter meter : meters) {
            // Lines that are too long to be ingested into Dynatrace, as well as lines
            // that contain NaN or Inf values are not returned from "toMetricLines",
            // and are therefore dropped.
            Stream<String> metricLines = toMetricLines(meter, seenMetadata);

            metricLines.forEach(line -> {
                batch.add(line);
                sendBatchIfFull(batch, partitionSize);
            });
        }

        // if the config to export metadata is turned off, the seenMetadata map will be
        // null.
        if (seenMetadata != null) {
            seenMetadata.values().forEach(line -> {
                if (line != null) {
                    batch.add(line);
                    sendBatchIfFull(batch, partitionSize);
                }
            });
        }

        // push remaining lines if any.
        if (!batch.isEmpty()) {
            send(batch);
        }
    }

    private void sendBatchIfFull(List<String> batch, int partitionSize) {
        if (batch.size() == partitionSize) {
            send(batch);
            batch.clear();
        }
    }

    private Stream<String> toMetricLines(Meter meter, Map<String, String> seenMetadata) {
        return meter.match(m -> toGaugeLine(m, seenMetadata), m -> toCounterLine(m, seenMetadata),
                m -> toTimerLine(m, seenMetadata), m -> toDistributionSummaryLine(m, seenMetadata),
                m -> toLongTaskTimerLine(m, seenMetadata), m -> toGaugeLine(m, seenMetadata),
                m -> toCounterLine(m, seenMetadata), m -> toFunctionTimerLine(m, seenMetadata),
                m -> toGaugeLine(m, seenMetadata));
    }

    Stream<String> toGaugeLine(Meter meter, Map<String, String> seenMetadata) {
        return toMeterLine(meter, (theMeter, measurement) -> createGaugeLine(theMeter, seenMetadata, measurement));
    }

    private String createGaugeLine(Meter meter, Map<String, String> seenMetadata, Measurement measurement) {
        try {
            double value = measurement.getValue();
            if (Double.isNaN(value)) {
                // NaNs can be caused by garbage collecting the backing field for a weak
                // reference or by setting the value of the backing field to NaN. At this
                // point it is impossible to distinguish these cases. This information is
                // logged once at WARN then on DEBUG level, as otherwise the serialization
                // would throw an exception and get logged at WARNING level. This can lead
                // to a lot of warning logs for the remainder of the application execution
                // if an object holding the weakly referenced objects has been garbage
                // collected, but the meter has not been removed from the registry.
                // NaN's are currently dropped on the Dynatrace side, so dropping them
                // on the client side here will not change the metrics in Dynatrace.
                logger.debug(
                        "Meter '{}' returned a value of NaN, which will not be exported. This can be a deliberate value or because the weak reference to the backing object expired.",
                        meter.getId().getName());
                return null;
            }
            MetricLineBuilder.GaugeStep gaugeStep = createTypeStep(meter).gauge();
            if (shouldExportMetadata(meter.getId())) {
                storeMetadata(enrichMetadata(gaugeStep.metadata(), meter), seenMetadata);
            }
            return gaugeStep.value(value).timestamp(Instant.ofEpochMilli(clock.wallTime())).build();
        }
        catch (MetricException e) {
            // logging at info to not drown out warnings/errors from business code.
            logger.info(METER_EXCEPTION_LOG_FORMAT, meter.getId(), e.getMessage());
        }

        return null;
    }

    Stream<String> toCounterLine(Meter counter, Map<String, String> seenMetadata) {
        return toMeterLine(counter, (meter, measurement) -> createCounterLine(meter, seenMetadata, measurement));
    }

    private String createCounterLine(Meter meter, Map<String, String> seenMetadata, Measurement measurement) {
        try {
            MetricLineBuilder.CounterStep counterStep = createTypeStep(meter).count();
            if (shouldExportMetadata(meter.getId())) {
                storeMetadata(enrichMetadata(counterStep.metadata(), meter), seenMetadata);
            }
            return counterStep.delta(measurement.getValue()).timestamp(Instant.ofEpochMilli(clock.wallTime())).build();
        }
        catch (MetricException e) {
            // logging at info to not drown out warnings/errors from business code.
            logger.info(METER_EXCEPTION_LOG_FORMAT, meter.getId(), e.getMessage());
        }

        return null;
    }

    Stream<String> toTimerLine(Timer meter, Map<String, String> seenMetadata) {
        if (!(meter instanceof DynatraceSummarySnapshotSupport)) {
            return toSummaryLine(meter, seenMetadata, meter.takeSnapshot(), getBaseTimeUnit());
        }

        DynatraceSummarySnapshot snapshot = ((DynatraceSummarySnapshotSupport) meter)
            .takeSummarySnapshotAndReset(getBaseTimeUnit());

        if (snapshot.getCount() == 0) {
            return Stream.empty();
        }

        return createSummaryLine(meter, seenMetadata, snapshot.getMin(), snapshot.getMax(), snapshot.getTotal(),
                snapshot.getCount());
    }

    private Stream<String> toSummaryLine(Meter meter, Map<String, String> seenMetadata,
            HistogramSnapshot histogramSnapshot, TimeUnit timeUnit) {
        long count = histogramSnapshot.count();
        if (count < 1) {
            logger.debug("Summary with 0 count dropped: {}", meter.getId().getName());
            return Stream.empty();
        }
        double total = (timeUnit != null) ? histogramSnapshot.total(timeUnit) : histogramSnapshot.total();
        double max = (timeUnit != null) ? histogramSnapshot.max(timeUnit) : histogramSnapshot.max();
        double min = (count == 1) ? max : minFromHistogramSnapshot(histogramSnapshot, timeUnit);
        return createSummaryLine(meter, seenMetadata, min, max, total, count);
    }

    private double minFromHistogramSnapshot(HistogramSnapshot histogramSnapshot, TimeUnit timeUnit) {
        ValueAtPercentile[] valuesAtPercentiles = histogramSnapshot.percentileValues();
        for (ValueAtPercentile valueAtPercentile : valuesAtPercentiles) {
            if (valueAtPercentile.percentile() == 0.0) {
                return (timeUnit != null) ? valueAtPercentile.value(timeUnit) : valueAtPercentile.value();
            }
        }
        return Double.NaN;
    }

    private Stream<String> createSummaryLine(Meter meter, Map<String, String> seenMetadata, double min, double max,
            double total, long count) {
        try {
            MetricLineBuilder.GaugeStep gaugeStep = createTypeStep(meter).gauge();
            if (shouldExportMetadata(meter.getId())) {
                storeMetadata(enrichMetadata(gaugeStep.metadata(), meter), seenMetadata);
            }
            return Stream.of(gaugeStep.summary(min, max, total, count)
                .timestamp(Instant.ofEpochMilli(clock.wallTime()))
                .build());
        }
        catch (MetricException e) {
            // logging at info to not drown out warnings/errors from business code.
            logger.info(METER_EXCEPTION_LOG_FORMAT, meter.getId(), e.getMessage());
        }

        return Stream.empty();
    }

    Stream<String> toDistributionSummaryLine(DistributionSummary meter, Map<String, String> seenMetadata) {
        if (!(meter instanceof DynatraceSummarySnapshotSupport)) {
            return toSummaryLine(meter, seenMetadata, meter.takeSnapshot(), null);
        }

        DynatraceSummarySnapshot snapshot = ((DynatraceSummarySnapshotSupport) meter).takeSummarySnapshotAndReset();

        if (snapshot.getCount() == 0) {
            return Stream.empty();
        }

        return createSummaryLine(meter, seenMetadata, snapshot.getMin(), snapshot.getMax(), snapshot.getTotal(),
                snapshot.getCount());
    }

    Stream<String> toLongTaskTimerLine(LongTaskTimer meter, Map<String, String> seenMetadata) {
        // use Dynatrace Snapshotting to ensure consistent data
        if (meter instanceof DynatraceSummarySnapshotSupport) {
            DynatraceSummarySnapshot snapshot = ((DynatraceSummarySnapshotSupport) meter)
                .takeSummarySnapshot(getBaseTimeUnit());
            if (snapshot.getCount() == 0) {
                return Stream.empty();
            }
            return createSummaryLine(meter, seenMetadata, snapshot.getMin(), snapshot.getMax(), snapshot.getTotal(),
                    snapshot.getCount());
        }

        // fall back to default implementation if the meter is not DynatraceLongTaskTimer
        HistogramSnapshot snapshot = meter.takeSnapshot();

        long count = snapshot.count();
        if (count == 0) {
            logger.debug("Timer with 0 count dropped: {}", meter.getId().getName());
            return Stream.empty();
        }
        else if (count == 1) {
            // In cases where the snapshot has only one value, often the min/max and sum
            // are not the same due to how this data is recorded. In the Dynatrace API,
            // this might lead to rejections, because the ingested data's validity is
            // checked. It is not possible to have a Dynatrace summary object with a
            // single value where min/max and sum are not equal.
            double total = snapshot.total(getBaseTimeUnit());
            return createSummaryLine(meter, seenMetadata, total, total, total, count);
        }

        return toSummaryLine(meter, seenMetadata, snapshot, getBaseTimeUnit());
    }

    Stream<String> toFunctionTimerLine(FunctionTimer meter, Map<String, String> seenMetadata) {
        long count = (long) meter.count();
        if (count == 0) {
            logger.debug("Timer with 0 count dropped: {}", meter.getId().getName());
            return Stream.empty();
        }

        double total = meter.totalTime(getBaseTimeUnit());
        if (count == 1) {
            // Between calling count, totalTime, and mean values can be recorded so the
            // reported values might be inconsistent. In the Dynatrace API,
            // this might lead to rejections, because the ingested data's validity is
            // checked. It is not possible to have a Dynatrace summary object with a
            // single value where min/max and sum are not equal.
            return createSummaryLine(meter, seenMetadata, total, total, total, count);
        }

        // Similarly, to the situation above, we are calculating avg here instead of
        // calling mean to avoid inconsistencies, i.e.: data was recorded between
        // calling count, totalTime, and mean.
        double average = total / count;
        return createSummaryLine(meter, seenMetadata, average, average, total, count);
    }

    private Stream<String> toMeterLine(Meter meter, BiFunction<Meter, Measurement, String> measurementConverter) {
        return streamOf(meter.measure()).map(measurement -> measurementConverter.apply(meter, measurement))
            .filter(Objects::nonNull);
    }

    private MetricLineBuilder.TypeStep createTypeStep(Meter meter) throws MetricException {
        MetricLineBuilder.TypeStep typeStep = MetricLineBuilder.create(preConfiguration)
            .metricKey(meter.getId().getName());
        for (Tag tag : meter.getId().getTags()) {
            typeStep.dimension(tag.getKey(), tag.getValue());
        }

        return typeStep;
    }

    private <T> Stream<T> streamOf(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private void send(List<String> metricLines) {
        String endpoint = config.uri();
        if (!isValidEndpoint(endpoint)) {
            logger.warn("Invalid endpoint, skipping export... ({})", endpoint);
            return;
        }
        try {
            int lineCount = metricLines.size();
            logger.debug("Sending {} lines to {}", lineCount, endpoint);

            String body = String.join("\n", metricLines);
            logger.debug("Sending lines:\n{}", body);

            HttpSender.Request.Builder requestBuilder = httpClient.post(endpoint);
            if (!shouldIgnoreToken(config)) {
                requestBuilder.withHeader("Authorization", "Api-Token " + config.apiToken());
            }

            requestBuilder.withHeader("User-Agent", "micrometer")
                .withPlainText(body)
                .send()
                .onSuccess(response -> handleSuccess(lineCount, response))
                .onError(response -> {
                    logger.info("Failed metric ingestion: Error Code={}, Response Body={}", response.code(),
                            getTruncatedBody(response));
                });
        }
        catch (Throwable throwable) {
            // logging at info to not drown out warnings/errors from business code.
            logger.info("Failed metric ingestion: {}", throwable.toString());
        }
    }

    private String getTruncatedBody(HttpSender.Response response) {
        return StringUtils.truncate(response.body(), 1_000, " (truncated)");
    }

    private void handleSuccess(int totalSent, HttpSender.Response response) {
        if (response.code() == 202) {
            if (IS_NULL_ERROR_RESPONSE.matcher(response.body()).find()) {
                Matcher linesOkMatchResult = EXTRACT_LINES_OK.matcher(response.body());
                Matcher linesInvalidMatchResult = EXTRACT_LINES_INVALID.matcher(response.body());
                if (linesOkMatchResult.find() && linesInvalidMatchResult.find()) {
                    logger.debug("Sent {} metric lines, linesOk: {}, linesInvalid: {}.", totalSent,
                            linesOkMatchResult.group(1), linesInvalidMatchResult.group(1));
                }
                else {
                    logger.warn("Unable to parse response: {}", getTruncatedBody(response));
                }
            }
            else {
                logger.warn("Unable to parse response: {}", getTruncatedBody(response));
            }
        }
        else {
            // common pitfall if URI is supplied in V1 format (without endpoint path)
            logger.error(
                    "Expected status code 202, got {}.\nResponse Body={}\nDid you specify the ingest path (e.g.: /api/v2/metrics/ingest)?",
                    response.code(), getTruncatedBody(response));
        }
    }

    /**
     * The metadata should be exported if it is enabled from config and at least one of
     * unit or description are set.
     * @param id meter Id
     * @return whether the metadata should be exported or not
     */
    private boolean shouldExportMetadata(Meter.Id id) {
        return config.exportMeterMetadata()
                && (!StringUtils.isEmpty(id.getBaseUnit()) || !StringUtils.isEmpty(id.getDescription()));
    }

    private MetricLineBuilder.MetadataStep enrichMetadata(MetricLineBuilder.MetadataStep metadataStep, Meter meter) {
        return metadataStep.description(meter.getId().getDescription())
            .unit(mapUnitIfNeeded(meter.getId().getBaseUnit()));
    }

    /**
     * Adds metadata found in {@link MetadataStep} to the {@code seenMetadata}.
     * @param metadataStep source of the metadata that should be added to
     * {@code seenMetadata}
     * @param seenMetadata destination of the metadata
     */
    private void storeMetadata(MetricLineBuilder.MetadataStep metadataStep, Map<String, String> seenMetadata) {
        // if the config to export metadata is turned off, seenMetadata will be null
        if (seenMetadata == null || metadataStep == null) {
            return;
        }

        String metadataLine = metadataStep.build();
        if (metadataLine == null) {
            return;
        }

        String key = extractMetricKey(metadataLine);
        if (!seenMetadata.containsKey(key)) {
            // if there is no metadata associated with the key, add it.
            seenMetadata.put(key, metadataLine);
        }
        else {
            String previousMetadataLine = seenMetadata.get(key);
            // if the previous line is not null, a metadata object had already been set in
            // the past and no conflicting metadata lines had been added thereafter.
            if (previousMetadataLine != null) {
                // if the new metadata line conflicts with the old one, we don't know
                // which one is the correct metadata and will not export any.
                // the map entry is set to null to ensure other metadata lines cannot be
                // set for this metric key.
                if (!previousMetadataLine.equals(metadataLine)) {
                    seenMetadata.put(key, null);
                    logger.debug(
                            "Metadata discrepancy detected:\n" + "original metadata:\t{}\n" + "tried to set new:\t{}\n"
                                    + "Metadata for metric key {} will not be sent.",
                            previousMetadataLine, metadataLine, key);
                }
            }
            // else:
            // the key exists, but the value is null, so a conflicting state has been
            // identified before. we will ignore any other metadata for this key, so there
            // is nothing to do here.
        }
    }

    private String extractMetricKey(String metadataLine) {
        if (metadataLine == null) {
            return null;
        }

        StringBuilder metricKey = new StringBuilder(32);
        // Start at index 1 as index 0 will always be '#'
        for (int i = 1; i < metadataLine.length(); i++) {
            char c = metadataLine.charAt(i);
            if (c == ' ' || c == ',') {
                break;
            }

            metricKey.append(c);
        }

        return metricKey.toString();
    }

    /**
     * Maps a unit string to a UCUM-compliant string, if the mapping is known, see:
     * {@link #ucumTimeUnitMap()}.
     * @param unit the unit that might be mapped
     * @return The UCUM-compliant string if known, otherwise returns the original unit
     */
    private static String mapUnitIfNeeded(String unit) {
        return unit != null ? UCUM_TIME_UNIT_MAP.getOrDefault(unit.toLowerCase(Locale.ROOT), unit) : null;
    }

    /**
     * Mapping from OpenJDK's {@link TimeUnit#toString()} and other common time unit
     * formats to UCUM-compliant format, see: <a href="https://ucum.org/">ucum.org</a>.
     * @return Time unit mapping to UCUM-compliant format
     */
    private static Map<String, String> ucumTimeUnitMap() {
        Map<String, String> mapping = new HashMap<>();
        // There are redundant elements in case the toString method of TimeUnit changes
        mapping.put(TimeUnit.NANOSECONDS.toString().toLowerCase(Locale.ROOT), "ns");
        mapping.put("nanoseconds", "ns");
        mapping.put("nanosecond", "ns");
        mapping.put(TimeUnit.MICROSECONDS.toString().toLowerCase(Locale.ROOT), "us");
        mapping.put("microseconds", "us");
        mapping.put("microsecond", "us");
        mapping.put(TimeUnit.MILLISECONDS.toString().toLowerCase(Locale.ROOT), "ms");
        mapping.put("milliseconds", "ms");
        mapping.put("millisecond", "ms");
        mapping.put(TimeUnit.SECONDS.toString().toLowerCase(Locale.ROOT), "s");
        mapping.put("seconds", "s");
        mapping.put("second", "s");
        mapping.put(TimeUnit.MINUTES.toString().toLowerCase(Locale.ROOT), "min");
        mapping.put("minutes", "min");
        mapping.put("minute", "min");
        mapping.put(TimeUnit.HOURS.toString().toLowerCase(Locale.ROOT), "h");
        mapping.put("hours", "h");
        mapping.put("hour", "h");

        return Collections.unmodifiableMap(mapping);
    }

}
