/**
 * Copyright 2021 VMware, Inc.
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

package io.micrometer.dynatrace.v2;

import com.dynatrace.metric.util.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.util.AbstractPartition;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.dynatrace.AbstractDynatraceExporter;
import io.micrometer.dynatrace.DynatraceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Georg Pirklbauer
 */
public final class DynatraceExporterV2 extends AbstractDynatraceExporter {
    private static final String METER_EXCEPTION_FORMAT = "Could not serialize meter %s: %s";
    private static final Pattern EXTRACT_LINES_OK = Pattern.compile("\"linesOk\":\\s?(\\d+)");
    private static final Pattern EXTRACT_LINES_INVALID = Pattern.compile("\"linesInvalid\":\\s?(\\d+)");
    private static final Pattern IS_NULL_ERROR_RESPONSE = Pattern.compile("\"error\":\\s?null");

    private static final Logger logger = LoggerFactory.getLogger(DynatraceExporterV2.class.getName());
    private static final Map<String, String> staticDimensions = Collections.singletonMap("dt.metrics.source", "micrometer");

    private final String endpoint;
    private final boolean ignoreToken;
    private final MetricBuilderFactory metricBuilderFactory;

    public DynatraceExporterV2(DynatraceConfig config, Clock clock, HttpSender httpClient) {
        super(config, clock, httpClient);
        this.endpoint = config.uri();
        showErrorIfEndpointIsInvalid(endpoint);
        ignoreToken = shouldIgnoreToken(config);
        logger.info("Exporting to endpoint {}", this.endpoint);

        MetricBuilderFactory.MetricBuilderFactoryBuilder factoryBuilder = MetricBuilderFactory.builder()
                .withPrefix(config.metricKeyPrefix())
                .withDefaultDimensions(parseDefaultDimensions(config.defaultDimensions()));

        if (config.enrichWithOneAgentMetadata()) {
            factoryBuilder.withOneAgentMetadata();
        }

        metricBuilderFactory = factoryBuilder.build();
    }

    private void showErrorIfEndpointIsInvalid(String uri) {
        try {
            URI.create(uri).toURL();
        } catch (IllegalArgumentException | MalformedURLException ex) {
            logger.error("Invalid URI provided, exporting will fail: {}", uri);
        }
    }

    private boolean shouldIgnoreToken(DynatraceConfig config) {
        if (config.apiToken().isEmpty()) {
            return true;
        } else if (config.uri().equals(DynatraceMetricApiConstants.getDefaultOneAgentEndpoint())) {
            logger.warn("Potential misconfiguration detected: Token is provided, but the endpoint is set to the local OneAgent endpoint, "
                    + "thus the token will be ignored. If exporting to the cluster API endpoint is intended, its URI has to be provided explicitly.");
            return true;
        } else {
            return false;
        }
    }

    private DimensionList parseDefaultDimensions(Map<String, String> defaultDimensions) {
        List<Dimension> dimensions = Stream.concat(
                defaultDimensions != null ? defaultDimensions.entrySet().stream() : Stream.empty(),
                staticDimensions.entrySet().stream()
        )
                .map(entry -> Dimension.create(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        return DimensionList.fromCollection(dimensions);
    }

    /**
     * Export to the Dynatrace v2 endpoint. Measurements that contain NaN or Infinite values, as
     * well as serialized data points that exceed length limits imposed by the API will be dropped
     * and not exported. If the number of serialized data points exceeds the maximum number of
     * allowed data points per request they will be sent in chunks.
     *
     * @param meters A list of {@link Meter Meters} that are serialized as one or more metric lines.
     */
    @Override
    public void export(@Nonnull List<Meter> meters) {
        // Lines that are too long to be ingested into Dynatrace, as well as lines that contain NaN
        // or Inf values are dropped and not returned from "toMetricLines", and are therefore dropped.
        List<String> metricLines = meters.stream()
                .flatMap(this::toMetricLines) // Stream<Meter> to Stream<String>
                .collect(Collectors.toList());

        sendInBatches(metricLines);
    }

    private Stream<String> toMetricLines(Meter meter) {
        return meter.match(
                this::toGaugeLine,
                this::toCounterLine,
                this::toTimerLine,
                this::toDistributionSummaryLine,
                this::toLongTaskTimerLine,
                this::toTimeGaugeLine,
                this::toFunctionCounterLine,
                this::toFunctionTimerLine,
                this::toMeterLine
        );
    }

    Stream<String> toGaugeLine(Gauge meter) {
        return toMeterLine(meter, this::createGaugeLine);
    }

    private String createGaugeLine(Meter meter, Measurement measurement) {
        try {
            return createMetricBuilder(meter).setDoubleGaugeValue(measurement.getValue()).serialize();
        } catch (MetricException e) {
            logger.warn(String.format(METER_EXCEPTION_FORMAT, meter.getId().getName(), e.getMessage()));
        }

        return null;
    }

    Stream<String> toCounterLine(Counter meter) {
        return toMeterLine(meter, this::createCounterLine);
    }

    private String createCounterLine(Meter meter, Measurement measurement) {
        try {
            return createMetricBuilder(meter).setDoubleCounterValueDelta(measurement.getValue()).serialize();
        } catch (MetricException e) {
            logger.warn(String.format(METER_EXCEPTION_FORMAT, meter.getId().getName(), e.getMessage()));
        }

        return null;
    }

    Stream<String> toTimerLine(Timer meter) {
        return toSummaryLine(meter, meter.takeSnapshot(), getBaseTimeUnit());
    }

    private Stream<String> toSummaryLine(Meter meter, HistogramSnapshot histogramSnapshot, TimeUnit timeUnit) {
        long count = histogramSnapshot.count();
        double total = (timeUnit != null) ? histogramSnapshot.total(timeUnit) : histogramSnapshot.total();
        double max = (timeUnit != null) ? histogramSnapshot.max(timeUnit) : histogramSnapshot.max();

        double min;
        if (count == 1) {
            min = max;
        } else {
            min = minFromHistogramSnapshot(histogramSnapshot, timeUnit);
        }

        return createSummaryLine(meter, min, max, total, count);
    }

    private double minFromHistogramSnapshot(HistogramSnapshot histogramSnapshot, TimeUnit timeUnit) {
        ValueAtPercentile[] valuesAtPercentiles = histogramSnapshot.percentileValues();
        double min = Double.NaN;

        for (ValueAtPercentile valueAtPercentile : valuesAtPercentiles) {
            if (valueAtPercentile.percentile() == 0.0) {
                min = (timeUnit != null) ? valueAtPercentile.value(timeUnit) : valueAtPercentile.value();
                break;
            }
        }

        return min;
    }

    private Stream<String> createSummaryLine(Meter meter, double min, double max, double total, long count) {
        try {
            String line = createMetricBuilder(meter).setDoubleSummaryValue(min, max, total, count).serialize();
            return streamOf(Collections.singletonList(line));
        } catch (MetricException e) {
            logger.warn(String.format(METER_EXCEPTION_FORMAT, meter.getId().getName(), e.getMessage()));
        }

        return Stream.empty();
    }

    Stream<String> toDistributionSummaryLine(DistributionSummary meter) {
        return toSummaryLine(meter, meter.takeSnapshot(), null);
    }

    Stream<String> toLongTaskTimerLine(LongTaskTimer meter) {
        return toSummaryLine(meter, meter.takeSnapshot(), getBaseTimeUnit());
    }

    Stream<String> toTimeGaugeLine(TimeGauge meter) {
        return toMeterLine(meter, this::createGaugeLine);
    }

    Stream<String> toFunctionCounterLine(FunctionCounter meter) {
        return toMeterLine(meter, this::createCounterLine);
    }

    Stream<String> toFunctionTimerLine(FunctionTimer meter) {
        double total = meter.totalTime(getBaseTimeUnit());
        double average = meter.mean(getBaseTimeUnit());
        long longCount = Double.valueOf(meter.count()).longValue();

        return createSummaryLine(meter, average, average, total, longCount);
    }

    Stream<String> toMeterLine(Meter meter) {
        return toMeterLine(meter, this::createGaugeLine);
    }

    private Stream<String> toMeterLine(Meter meter, BiFunction<Meter, Measurement, String> measurementConverter) {
        return streamOf(meter.measure())
                .map(measurement -> measurementConverter.apply(meter, measurement))
                .filter(Objects::nonNull);
    }

    private Metric.Builder createMetricBuilder(Meter meter) {
        return metricBuilderFactory.newMetricBuilder(meter.getId().getName())
                .setDimensions(fromTags(meter.getId().getTags()))
                .setTimestamp(Instant.ofEpochMilli(clock.wallTime()));
    }

    private DimensionList fromTags(List<Tag> tags) {
        return DimensionList.fromCollection(tags.stream()
                .map(tag -> Dimension.create(tag.getKey(), tag.getValue()))
                .collect(Collectors.toList())
        );
    }

    private <T> Stream<T> streamOf(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private void send(List<String> metricLines) {
        try {
            String body = String.join("\n", metricLines);
            if (logger.isDebugEnabled()) {
                logger.debug("sending lines:\n" + body);
            }

            HttpSender.Request.Builder requestBuilder = httpClient.post(endpoint);
            if (!ignoreToken) {
                requestBuilder.withHeader("Authorization", "Api-Token " + config.apiToken());
            }

            requestBuilder
                    .withHeader("User-Agent", "micrometer")
                    .withPlainText(body)
                    .send()
                    .onSuccess(response -> handleSuccess(metricLines.size(), response))
                    .onError(response -> logger.error("Failed metric ingestion. Error code={} response.body={}", response.code(), response.body()));
        } catch (Throwable throwable) {
            logger.error("Failed metric ingestion: {}", throwable.getMessage());
        }
    }

    private void handleSuccess(int totalSent, HttpSender.Response response) {
        if (response.code() == 202) {
            if (IS_NULL_ERROR_RESPONSE.matcher(response.body()).find()) {
                Matcher linesOkMatchResult = EXTRACT_LINES_OK.matcher(response.body());
                Matcher linesInvalidMatchResult = EXTRACT_LINES_INVALID.matcher(response.body());
                if (linesOkMatchResult.find() && linesInvalidMatchResult.find()) {
                    logger.info("Sent {} metric lines, linesOk: {}, linesInvalid: {}.",
                            totalSent, linesOkMatchResult.group(1), linesInvalidMatchResult.group(1));
                } else {
                    logger.warn("Unable to parse response: {}", response.body());
                }
            } else {
                logger.warn("Unable to parse response: {}", response.body());
            }
        } else {
            // common pitfall if URI is supplied in V1 format (without endpoint path)
            logger.error("Expected status code 202, got {}. Did you specify the ingest path (e.g.: /api/v2/metrics/ingest)?", response.code());
        }
    }

    private void sendInBatches(List<String> metricLines) {
        int partitionSize = Math.min(config.batchSize(), DynatraceMetricApiConstants.getPayloadLinesLimit());
        MetricLinePartition.partition(metricLines, partitionSize).forEach(this::send);
    }

    static class MetricLinePartition extends AbstractPartition<String> {

        private MetricLinePartition(List<String> list, int partitionSize) {
            super(list, partitionSize);
        }

        static List<List<String>> partition(List<String> list, int partitionSize) {
            return new MetricLinePartition(list, partitionSize);
        }
    }
}
