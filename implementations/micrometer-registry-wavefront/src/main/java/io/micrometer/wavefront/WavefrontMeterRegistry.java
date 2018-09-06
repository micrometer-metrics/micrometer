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
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
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
                        con.setConnectTimeout((int) config.connectTimeout().toMillis());
                        con.setReadTimeout((int) config.readTimeout().toMillis());
                        con.setDoOutput(true);
                        con.addRequestProperty("Content-Type", "application/json");
                        con.addRequestProperty("Accept", "application/json");

                        try (OutputStream os = con.getOutputStream();
                             OutputStreamWriter writer = new OutputStreamWriter(os, "UTF-8")) {
                            writer.write("{" + stream.collect(joining(",")) + "}");
                            writer.flush();
                        }

                        int status = con.getResponseCode();
                        if (status >= 200 && status < 300) {
                            logger.info("successfully sent {} metrics to Wavefront", batch.size());
                        } else {
                            try (InputStream in = con.getErrorStream()) {
                                logger.error("failed to send metrics: " + new BufferedReader(new InputStreamReader(in))
                                        .lines().collect(joining("\n")));
                            }
                        }
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    } finally {
                        quietlyCloseUrlConnection(con);
                    }
                } else {
                    SocketAddress endpoint = getSocketAddress(uri.getHost(), uri.getPort());
                    int timeout = (int) this.config.connectTimeout().toMillis();

                    try (Socket socket = new Socket()) {
                        socket.connect(endpoint, timeout);
                        try (OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), "UTF-8")) {
                          writer.write(stream.collect(joining("\n")) + "\n");
                          writer.flush();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.warn("failed to send metrics", t);
        }
    }

    private static SocketAddress getSocketAddress(String host, int port) throws UnknownHostException {
        return host != null ? new InetSocketAddress(host, port) :
                new InetSocketAddress(InetAddress.getByName(null), port);
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
        Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = timer.getId();

        // we can't know anything about max and percentiles originating from a function timer
        addMetric(metrics, id, "count", wallTime, timer.count());
        addMetric(metrics, id, "avg", wallTime, timer.mean(getBaseTimeUnit()));
        addMetric(metrics, id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()));

        return metrics.build();
    }

    private Stream<String> writeTimer(Timer timer) {
        final long wallTime = clock.wallTime();
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = timer.getId();
        addMetric(metrics, id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()));
        addMetric(metrics, id, "count", wallTime, timer.count());
        addMetric(metrics, id, "avg", wallTime, timer.mean(getBaseTimeUnit()));
        addMetric(metrics, id, "max", wallTime, timer.max(getBaseTimeUnit()));

        return metrics.build();
    }

    private Stream<String> writeSummary(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = summary.getId();
        addMetric(metrics, id, "sum", wallTime, summary.totalAmount());
        addMetric(metrics, id, "count", wallTime, summary.count());
        addMetric(metrics, id, "avg", wallTime, summary.mean());
        addMetric(metrics, id, "max", wallTime, summary.max());

        return metrics.build();
    }

    private Stream<String> writeMeter(Meter meter) {
        long wallTime = clock.wallTime();
        Stream.Builder<String> metrics = Stream.builder();

        stream(meter.measure().spliterator(), false)
                .forEach(measurement -> {
                    Meter.Id id = meter.getId().withTag(measurement.getStatistic());
                    addMetric(metrics, id, null, wallTime, measurement.getValue());
                });

        return metrics.build();
    }

    private void addMetric(Stream.Builder<String> metrics, Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        if (value != Double.NaN) {
            metrics.add(writeMetric(id, suffix, wallTime, value));
        }
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
