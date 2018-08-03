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
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.HttpHeader;
import io.micrometer.core.instrument.util.IOUtils;
import io.micrometer.core.instrument.util.MediaType;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 * @author Howard Yoo
 */
public class WavefrontMeterRegistry extends StepMeterRegistry {
    private final Logger logger = LoggerFactory.getLogger(WavefrontMeterRegistry.class);
    private final WavefrontConfig config;
    private final URI uri;
    private final boolean directToApi;

    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;
        this.uri = URI.create(config.uri());
        this.directToApi = !"proxy".equals(uri.getScheme());

        if (directToApi && config.apiToken() == null) {
            throw new MissingRequiredConfigurationException("apiToken must be set whenever publishing directly to the Wavefront API");
        }

        config().namingConvention(new WavefrontNamingConvention(config.globalPrefix()));

        start(threadFactory);
    }

    @Override
    protected void publish() {
        try {
            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                Stream<String> stream =
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
                        });

                if (directToApi) {
                    HttpURLConnection con = null;
                    try {
                        URL url = new URL(uri.getScheme(), uri.getHost(), uri.getPort(), String.format("/report/metrics?t=%s&h=%s", config.apiToken(), config.source()));
                        con = (HttpURLConnection) url.openConnection();
                        con.setDoOutput(true);
                        con.addRequestProperty(HttpHeader.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                        con.addRequestProperty(HttpHeader.ACCEPT, MediaType.APPLICATION_JSON);

                        try (OutputStream os = con.getOutputStream();
                             OutputStreamWriter writer = new OutputStreamWriter(os, "UTF-8")) {
                            writer.write("{" + stream.collect(joining(",")) + "}");
                            writer.flush();
                        }

                        int status = con.getResponseCode();
                        if (status >= 200 && status < 300) {
                            logger.info("successfully sent {} metrics to Wavefront", batch.size());
                        } else {
                            if (logger.isErrorEnabled()) {
                                logger.error("failed to send metrics: {}", IOUtils.toString(con.getErrorStream()));
                            }
                        }
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    } finally {
                        quietlyCloseUrlConnection(con);
                    }
                } else {
                    try (Socket socket = new Socket(uri.getHost(), uri.getPort());
                         OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), "UTF-8")) {
                        writer.write(stream.collect(joining("\n")) + "\n");
                        writer.flush();
                    }
                }
            }
        } catch (Throwable t) {
            logger.warn("failed to send metrics", t);
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
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = timer.getId();
        metrics.add(writeMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit())));
        metrics.add(writeMetric(id, "count", wallTime, timer.count()));
        metrics.add(writeMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit())));
        metrics.add(writeMetric(id, "max", wallTime, timer.max(getBaseTimeUnit())));

        return metrics.build();
    }

    private Stream<String> writeSummary(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = summary.getId();
        metrics.add(writeMetric(id, "sum", wallTime, summary.totalAmount()));
        metrics.add(writeMetric(id, "count", wallTime, summary.count()));
        metrics.add(writeMetric(id, "avg", wallTime, summary.mean()));
        metrics.add(writeMetric(id, "max", wallTime, summary.max()));

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
     * The metric format is a little different depending on whether you are going straight to the
     * Wavefront API server or through a sidecar proxy.
     * <p>
     * https://docs.wavefront.com/wavefront_data_format.html#wavefront-data-format-syntax
     */
    private String writeMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        return directToApi ?
                writeMetricDirect(id, suffix, value) :
                writeMetricProxy(id, suffix, wallTime, value);
    }

    private String writeMetricProxy(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        // surrounding the name with double quotes allows for / and , in names
        return "\"" + getConventionName(fullId) + "\" " + DoubleFormat.decimalOrNan(value) + " " + (wallTime / 1000) +
                " source=" + config.source() + " " +
                getConventionTags(fullId)
                        .stream()
                        .map(t -> t.getKey() + "=\"" + t.getValue() + "\"")
                        .collect(joining(" "));
    }

    private String writeMetricDirect(Meter.Id id, @Nullable String suffix, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        List<Tag> conventionTags = getConventionTags(fullId);

        String tags = conventionTags
                .stream()
                .map(t -> "\"" + t.getKey() + "\": \"" + t.getValue() + "\"")
                .collect(joining(","));

        UUID uuid = UUID.randomUUID();
        String uniqueNameSuffix = ((Long) uuid.getMostSignificantBits()).toString() + uuid.getLeastSignificantBits();

        // To be valid JSON, the metric name must be unique. Since the same name can occur in multiple entries because of
        // variance in tag values, we need to append a suffix to the name. The suffix must be numeric, or Wavefront interprets
        // it as part of the name. Wavefront strips a $<NUMERIC> suffix from the name at parsing time.
        return "\"" + getConventionName(fullId) + "$" + uniqueNameSuffix + "\"" +
                ": {" +
                "\"value\": " + DoubleFormat.decimalOrNan(value) + "," +
                "\"tags\": {" + tags + "}" +
                "}";
    }

    /**
     * Copy tags, unit, and description from an existing id, but change the name.
     */
    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return new Meter.Id(id.getName() + "." + suffix, id.getTags(), id.getBaseUnit(), id.getDescription(), id.getType());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }
}
