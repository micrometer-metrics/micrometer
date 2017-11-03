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
package io.micrometer.datadog;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.histogram.HistogramConfig;
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
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class DatadogMeterRegistry extends StepMeterRegistry {
    private final URL metricsEndpoint;
    private final Logger logger = LoggerFactory.getLogger(DatadogMeterRegistry.class);
    private final DatadogConfig config;
    private final DecimalFormat percentileFormat = new DecimalFormat("#.####");
    private final Map<Meter, HistogramConfig> histogramConfigs = new ConcurrentHashMap<>();

    public DatadogMeterRegistry(DatadogConfig config, Clock clock) {
        super(config, clock);

        this.config().namingConvention(new DatadogNamingConvention());

        try {
            this.metricsEndpoint = URI.create(config.uri()).toURL();
        } catch (MalformedURLException e) {
            // not possible
            throw new RuntimeException(e);
        }

        this.config = config;

        start();
    }

    public DatadogMeterRegistry(DatadogConfig config) {
        this(config, Clock.SYSTEM);
    }

    @Override
    protected void publish() {
        try {
            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                HttpURLConnection con = (HttpURLConnection) metricsEndpoint.openConnection();
                con.setConnectTimeout((int) config.connectTimeout().toMillis());
                con.setReadTimeout((int) config.readTimeout().toMillis());
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);

                /*
                Example post body from Datadog API docs. Type seems to be irrelevant. Host and tags are optional.
                "{ \"series\" :
                        [{\"metric\":\"test.metric\",
                          \"points\":[[$currenttime, 20]],
                          \"type\":\"gauge\",
                          \"host\":\"test.example.com\",
                          \"tags\":[\"environment:test\"]}
                        ]
                }"
                */

                String body = "{\"series\":[" +
                    batch.stream().flatMap(m -> {
                        if (m instanceof Timer) {
                            return writeTimer((Timer) m);
                        } else if (m instanceof DistributionSummary) {
                            return writeSummary((DistributionSummary) m);
                        } else if (m instanceof FunctionTimer) {
                            return writeTimer((FunctionTimer) m);
                        } else {
                            return writeMeter(m);
                        }
                    }).collect(joining(",")) +
                    "]}";

                try (OutputStream os = con.getOutputStream()) {
                    os.write(body.getBytes());
                    os.flush();
                }

                int status = con.getResponseCode();

                if (status >= 200 && status < 300) {
                    logger.info("successfully sent " + batch.size() + " metrics to datadog");
                } else if (status >= 400) {
                    try (InputStream in = con.getErrorStream()) {
                        logger.error("failed to send metrics: " + new BufferedReader(new InputStreamReader(in))
                            .lines().collect(joining("\n")));
                    }
                } else {
                    logger.error("failed to send metrics: http " + status);
                }

                con.disconnect();
            }
        } catch (Exception e) {
            logger.warn("failed to send metrics", e);
        }
    }

    private Stream<String> writeTimer(FunctionTimer timer) {
        long wallTime = clock.wallTime();

        // we can't know anything about max and percentiles originating from a function timer
        return Stream.of(
            writeMetric(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()),
            writeMetric(idWithSuffix(timer.getId(), "avg"), wallTime, timer.mean(getBaseTimeUnit())));
    }

    private Stream<String> writeTimer(Timer timer) {
        long wallTime = clock.wallTime();

        Stream.Builder<String> metrics = Stream.builder();

        metrics.add(writeMetric(idWithSuffix(timer.getId(), "sum"), wallTime, timer.totalTime(getBaseTimeUnit())));
        metrics.add(writeMetric(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()));
        metrics.add(writeMetric(idWithSuffix(timer.getId(), "avg"), wallTime, timer.mean(getBaseTimeUnit())));
        metrics.add(writeMetric(idWithSuffix(timer.getId(), "max"), wallTime, timer.max(getBaseTimeUnit())));

        for (double percentile : histogramConfigs.get(timer).getPercentiles()) {
            metrics.add(writeMetric(idWithSuffix(timer.getId(), percentileFormat.format(percentile) + "percentile"), wallTime, timer.percentile(percentile, getBaseTimeUnit())));
        }

        return metrics.build();
    }

    private Stream<String> writeSummary(DistributionSummary summary) {
        long wallTime = clock.wallTime();

        Stream.Builder<String> metrics = Stream.builder();

        metrics.add(writeMetric(idWithSuffix(summary.getId(), "sum"), wallTime, summary.totalAmount()));
        metrics.add(writeMetric(idWithSuffix(summary.getId(), "count"), wallTime, summary.count()));
        metrics.add(writeMetric(idWithSuffix(summary.getId(), "avg"), wallTime, summary.mean()));
        metrics.add(writeMetric(idWithSuffix(summary.getId(), "max"), wallTime, summary.max()));

        for (double percentile : histogramConfigs.get(summary).getPercentiles()) {
            metrics.add(writeMetric(idWithSuffix(summary.getId(),percentileFormat.format(percentile) + "percentile"), wallTime, summary.percentile(percentile)));
        }

        return metrics.build();
    }

    private Stream<String> writeMeter(Meter m) {
        long wallTime = clock.wallTime();
        return stream(m.measure().spliterator(), false)
            .map(ms -> writeMetric(m.getId().withTag(ms.getStatistic()), wallTime, ms.getValue()));
    }

    private String writeMetric(Meter.Id id, long wallTime, double value) {
        Iterable<Tag> tags = id.getTags();

        String host = config.hostTag() == null ? "" : stream(tags.spliterator(), false)
            .filter(t -> config.hostTag().equals(t.getKey()))
            .findAny()
            .map(t -> ",\"host\":" + t.getValue())
            .orElse("");

        String tagsArray = tags.iterator().hasNext() ?
            ",\"tags\":[" +
                stream(tags.spliterator(), false)
                    .map(t -> "\"" + t.getKey() + ":" + t.getValue() + "\"")
                    .collect(joining(",")) + "]" : "";

        return "{\"metric\":\"" + id.getConventionName(config().namingConvention()) + "\"," +
            "\"points\":[[" + (wallTime / 1000) + ", " + value + "]]" + host + tagsArray + "}";
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    /**
     * Copy tags, unit, and description from an existing id, but change the name.
     */
    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return new Meter.Id(id.getName() + "." + suffix, id.getTags(), id.getBaseUnit(), id.getDescription());
    }
}
