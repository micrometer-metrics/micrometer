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
package io.micrometer.wavefront;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.Nullable;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

public class WavefrontMeterRegistry extends StepMeterRegistry {
    private final Logger logger = LoggerFactory.getLogger(WavefrontMeterRegistry.class);
    private final WavefrontConfig config;
    private final URL postMetricsEndpoint;

    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;

        try {
            this.postMetricsEndpoint = URI.create(config.uri()).toURL();
        } catch (MalformedURLException e) {
            // not possible
            throw new RuntimeException(e);
        }

        config().namingConvention(new WavefrontNamingConvention());

        if(config.enabled())
            start(threadFactory);
    }

    @Override
    protected void publish() {
        try {
            HttpURLConnection con = null;

            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                try {
                    con = (HttpURLConnection) postMetricsEndpoint.openConnection();
                    con.setConnectTimeout((int) config.connectTimeout().toMillis());
                    con.setReadTimeout((int) config.readTimeout().toMillis());
                    con.setRequestMethod("POST");
                    con.setRequestProperty("Authorization", "Bearer " + config.apiToken());
                    con.setRequestProperty("Content-Type", "text/plain");
                    con.setDoOutput(true);

                    String body =
                        batch.stream().flatMap(m -> {
                            if (m instanceof Timer) {
                                return writeTimer((Timer) m);
                            }
                            if (m instanceof DistributionSummary) {
                                return writeSummary((DistributionSummary) m);
                            }
                            if (m instanceof FunctionTimer) {
                                return writeTimer((FunctionTimer) m);
                            }
                            return writeMeter(m);
                        }).collect(joining("\n"));

                    try (OutputStream os = con.getOutputStream()) {
                        os.write(body.getBytes());
                        os.flush();
                    }

                    int status = con.getResponseCode();

                    if (status >= 200 && status < 300) {
                        logger.info("successfully sent " + batch.size() + " metrics to Wavefront");
                    } else if (status >= 400) {
                        try (InputStream in = con.getErrorStream()) {
                            logger.error("failed to send metrics: " + new BufferedReader(new InputStreamReader(in))
                                .lines().collect(joining("\n")));
                        }
                    } else {
                        logger.error("failed to send metrics: http " + status);
                    }
                } finally {
                    quietlyCloseUrlConnection(con);
                }
            }
        } catch (Exception e) {
            logger.warn("failed to send metrics", e);
        }
    }

    private void quietlyCloseUrlConnection(@Nullable HttpURLConnection con) {
        try {
            if (con != null) {
                con.disconnect();
            }
        } catch (Exception ignore) {
        }
    }

    private Stream<String> writeTimer(FunctionTimer timer) {
        long wallTime = clock.wallTime();

        Meter.Id id = timer.getId();

        // we can't know anything about max and percentiles originating from a function timer
        return Stream.of(
            writeMetric(id, "count", wallTime, timer.count()),
            writeMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit())),
            writeMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    private Stream<String> writeTimer(Timer timer) {
        final long wallTime = clock.wallTime();
        final HistogramSnapshot snapshot = timer.takeSnapshot(false);
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = timer.getId();
        metrics.add(writeMetric(id, "sum", wallTime, snapshot.total(getBaseTimeUnit())));
        metrics.add(writeMetric(id, "count", wallTime, snapshot.count()));
        metrics.add(writeMetric(id, "avg", wallTime, snapshot.mean(getBaseTimeUnit())));
        metrics.add(writeMetric(id, "max", wallTime, snapshot.max(getBaseTimeUnit())));

        for (ValueAtPercentile v : snapshot.percentileValues()) {
            String suffix = DoubleFormat.toString(v.percentile() * 100) + "percentile";
            metrics.add(writeMetric(id, suffix, wallTime, v.value(getBaseTimeUnit())));
        }

        // FIXME write histogram format here
        for (CountAtValue countAtValue : snapshot.histogramCounts()) {
            // countAtValue represents a single histogram bucket
        }

        return metrics.build();
    }

    private Stream<String> writeSummary(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final HistogramSnapshot snapshot = summary.takeSnapshot(false);
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = summary.getId();
        metrics.add(writeMetric(id, "sum", wallTime, snapshot.total()));
        metrics.add(writeMetric(id, "count", wallTime, snapshot.count()));
        metrics.add(writeMetric(id, "avg", wallTime, snapshot.mean()));
        metrics.add(writeMetric(id, "max", wallTime, snapshot.max()));

        for (ValueAtPercentile v : snapshot.percentileValues()) {
            String suffix = DoubleFormat.toString(v.percentile() * 100) + "percentile";
            metrics.add(writeMetric(id, suffix, wallTime, v.value()));
        }

        // FIXME write histogram format here
        for (CountAtValue countAtValue : snapshot.histogramCounts()) {
            // countAtValue represents a single histogram bucket
        }

        return metrics.build();
    }

    private Stream<String> writeMeter(Meter m) {
        long wallTime = clock.wallTime();
        return stream(m.measure().spliterator(), false)
            .map(ms -> {
                Meter.Id id = m.getId().withTag(ms.getStatistic());
                return writeMetric(id, null, wallTime, ms.getValue());
            });
    }

    /**
     * https://docs.wavefront.com/wavefront_data_format.html#wavefront-data-format-syntax
     */
    //VisibleForTesting
    String writeMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        // FIXME should we derive HOST from a common tag?
        return getConventionName(id) + " " + DoubleFormat.toString(value) + " " + wallTime + " HOST " +
            getConventionTags(fullId)
                .stream()
                .map(t -> t.getKey() + "=\"" + t.getValue() + "\"")
                .collect(Collectors.joining(" "));

    }

    /**
     * Copy tags, unit, and description from an existing id, but change the name.
     */
    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return new Meter.Id(id.getName() + "." + suffix, id.getTags(), id.getBaseUnit(), id.getDescription(), id.getType());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        // TODO what is the typical base unit of time?
        return TimeUnit.SECONDS;
    }
}
