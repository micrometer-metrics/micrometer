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

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.*;
import java.util.List;
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
    private final String wavefrontProxyHost;
    private final String wavefrontProxyPort;

    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;

        try {
            this.wavefrontProxyHost = config.host();
            this.wavefrontProxyPort = config.port();
        } catch (Exception e) {
            // not possible
            throw new RuntimeException(e);
        }

        config().namingConvention(new WavefrontNamingConvention(config.globalPrefix()));

        if (config.enabled()) {
            start(threadFactory);
        }
    }

    @Override
    protected void publish() {
        try {
            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {

                StringBuffer buffer = new StringBuffer();
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

                if(config.mode() == WavefrontConfig.mode.proxy)
                {
                    buffer.append(stream.collect(joining("\n"))).append("\n");
                    try (Socket socket = new Socket(wavefrontProxyHost, Integer.parseInt(wavefrontProxyPort));
                         OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), "UTF-8")) {
                        writer.write(buffer.toString());
                        writer.flush();
                    }
                }
                else if(config.mode() == WavefrontConfig.mode.direct)
                {
                    buffer.append("{").append(stream.collect(joining(","))).append("}");
                    String apitoken = config.apitoken();
                    String apihost = config.apihost();
                    if(apitoken != null && apihost != null) {
                        HttpsURLConnection conn = null;
                        OutputStreamWriter writer = null;
                        try {
                            URL url = new URL("https", apihost, 443, String.format("/report/metrics?t=%s&h=%s", apitoken, config.source()));
                            conn = (HttpsURLConnection)url.openConnection();
                            conn.setDoOutput(true);
                            conn.addRequestProperty("Content-Type", "application/json");
                            conn.addRequestProperty("Accept", "application/json");
                            writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
                            writer.write(buffer.toString());
                            writer.flush();

                            int responseCode = conn.getResponseCode();
                            if(responseCode == 204) {
                                // success
                            }
                            else {
                                // log error
                                logger.error("publish failed when reporting directly to wavefront. Error code:" + responseCode);
                            }
                        }
                        catch(Exception e) {
                            logger.error(e.getMessage(), e);
                        }
                        finally {
                            try {
                                if(writer != null) writer.close();
                                if(conn != null) conn.disconnect();
                            }
                            catch(Exception e) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("failed to send metrics", e);
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

        if(config.enableHistograms()) {
            // FIXME write histogram format here
            for (CountAtValue countAtValue : snapshot.histogramCounts()) {
                // countAtValue represents a single histogram bucket
            }
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

        if (config.enableHistograms()) {
            // FIXME write histogram format here
            for (CountAtValue countAtValue : snapshot.histogramCounts()) {
                // countAtValue represents a single histogram bucket
            }
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
    private String writeMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        String result = null;
        if(config.mode() == WavefrontConfig.mode.proxy) {
            result = writeMetricProxy(id, suffix, wallTime, value);
        }
        else if(config.mode() == WavefrontConfig.mode.direct) {
            result = writeMetricDirect(id, suffix, wallTime, value);
        }
        else {
            result = "";
        }
        return result;
    }

    private String writeMetricProxy(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        // surrounding the name with double quotes allows for / and , in names
        return "\"" + getConventionName(fullId) + "\" " + DoubleFormat.toString(value) + " " + (wallTime / 1000) +
            " source=" + config.source() + " " +
            getConventionTags(fullId)
                .stream()
                .map(t -> t.getKey() + "=\"" + t.getValue() + "\"")
                .collect(joining(" "));
    }

    private String writeMetricDirect(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        StringBuffer buffer = new StringBuffer();

        // metric name
        buffer.append("\"").append(getConventionName(fullId)).append("\"");
        buffer.append(": {");

        // value
        buffer.append("\"value\": ").append(DoubleFormat.toString(value));

        // metric tags
        buffer.append(",\"tags\": {");
        buffer.append(getConventionTags(fullId)
            .stream()
            .map(t -> "\"" + t.getKey() + "\": \"" + t.getValue() + "\"")
            .collect(joining(",")));
        buffer.append("}");

        buffer.append("}");
        return buffer.toString();
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
