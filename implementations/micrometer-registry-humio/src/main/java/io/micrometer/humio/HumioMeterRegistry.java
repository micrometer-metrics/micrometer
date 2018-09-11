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
package io.micrometer.humio;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.HttpHeader;
import io.micrometer.core.instrument.util.HttpMethod;
import io.micrometer.core.instrument.util.IOUtils;
import io.micrometer.core.instrument.util.MediaType;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author Martin Westergaard Lassen
 */
public class HumioMeterRegistry extends StepMeterRegistry {

    private final Logger logger = LoggerFactory.getLogger(HumioMeterRegistry.class);

    static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final HumioConfig config;
    private final String repository;
    private final String apiToken;

    public HumioMeterRegistry(HumioConfig config, Clock clock, NamingConvention namingConvention, ThreadFactory threadFactory) {
        super(config, clock);
        this.config().namingConvention(namingConvention);
        this.config = config;

        this.repository = config.repository();
        this.apiToken = config.apiToken();

        start(threadFactory);
    }

    public HumioMeterRegistry(HumioConfig config, Clock clock) {
        this(config, clock, new HumioNamingConvention(), Executors.defaultThreadFactory());
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            long wallTime = config().clock().wallTime();

            HttpURLConnection connection = openConnection("/api/v1/dataspaces/" + repository + "/ingest");

            if (connection == null) {
                if (logger.isErrorEnabled()) {
                    logger.error("Could not connect to configured Humio: {}", config.host());
                }
                return;
            }

            try (OutputStream outputStream = connection.getOutputStream()) {
                boolean first = true;
                outputStream.write("[{\"tags\": {\"type\":\"micrometrics\"},\"events\":[".getBytes());
                for (Meter m : batch) {
                    if (first) {
                        first = false;
                    }
                    else {
                        outputStream.write(',');
                    }
                    if (m instanceof TimeGauge) {
                        writeGauge(outputStream, (TimeGauge) m, wallTime);
                    } else if (m instanceof Gauge) {
                        writeGauge(outputStream, (Gauge) m, wallTime);
                    } else if (m instanceof Counter) {
                        writeCounter(outputStream, (Counter) m, wallTime);
                    } else if (m instanceof FunctionCounter) {
                        writeCounter(outputStream, (FunctionCounter) m, wallTime);
                    } else if (m instanceof Timer) {
                        writeTimer(outputStream, (Timer) m, wallTime);
                    } else if (m instanceof FunctionTimer) {
                        writeTimer(outputStream, (FunctionTimer) m, wallTime);
                    } else if (m instanceof DistributionSummary) {
                        writeSummary(outputStream, (DistributionSummary) m, wallTime);
                    } else if (m instanceof LongTaskTimer) {
                        writeLongTaskTimer(outputStream, (LongTaskTimer) m, wallTime);
                    } else {
                        writeMeter(outputStream, m, wallTime);
                    }
                }
                outputStream.write("]}]".getBytes());
                outputStream.flush();

                if (connection.getResponseCode() >= 400) {
                    if (logger.isErrorEnabled()) {
                        try {
                            logger.error("failed to send metrics to Humio (HTTP {}). Cause: {}", connection.getResponseCode(), IOUtils.toString(connection.getErrorStream(), StandardCharsets.UTF_8));
                        } catch (IOException ignored) {
                        }
                    }
                    return; // don't try another batch
                } else {
                    logger.info("successfully sent {} metrics to Humio", batch.size());
                }
            } catch (IOException e) {
                logger.error("Could not serialize meter", e);
                return;
            } finally {
                connection.disconnect();
            }
        }
    }

    // VisibleForTesting
    void writeCounter(OutputStream os, Counter counter, long wallTime) throws IOException {
        writeEvent(os, counter, wallTime, builder -> {
            builder.append(",\"count\":").append(counter.count());
        });
    }

    // VisibleForTesting
    void writeCounter(OutputStream os, FunctionCounter counter, long wallTime) throws IOException {
        writeEvent(os, counter, wallTime, builder -> {
            builder.append(",\"count\":").append(counter.count());
        });
    }

    // VisibleForTesting
    void writeGauge(OutputStream os, Gauge gauge, long wallTime) throws IOException {
        Double value = gauge.value();
        if (!value.isNaN()) {
            writeEvent(os, gauge, wallTime, builder -> {
                builder.append(",\"value\":").append(value);
            });
        }
    }

    // VisibleForTesting
    void writeGauge(OutputStream os, TimeGauge gauge, long wallTime) throws IOException {
        Double value = gauge.value();
        if (!value.isNaN()) {
            writeEvent(os, gauge, wallTime, builder -> {
                builder.append(",\"value\":").append(gauge.value(getBaseTimeUnit()));
            });
        }
    }

    // VisibleForTesting
    void writeTimer(OutputStream os, FunctionTimer timer, long wallTime) throws IOException {
        writeEvent(os, timer, wallTime, builder -> {
            builder.append(",\"count\":").append(timer.count());
            builder.append(",\"sum\" :").append(timer.totalTime(getBaseTimeUnit()));
            builder.append(",\"mean\":").append(timer.mean(getBaseTimeUnit()));
        });
    }

    // VisibleForTesting
    void writeLongTaskTimer(OutputStream os, LongTaskTimer timer, long wallTime) throws IOException {
        writeEvent(os, timer, wallTime, builder -> {
            builder.append(",\"activeTasks\":").append(timer.activeTasks());
            builder.append(",\"duration\":").append(timer.duration(getBaseTimeUnit()));
        });
    }

    // VisibleForTesting
    void writeTimer(OutputStream os, Timer timer, long wallTime) throws IOException {
        writeEvent(os, timer, wallTime, builder -> {
            builder.append(",\"count\":").append(timer.count());
            builder.append(",\"sum\":").append(timer.totalTime(getBaseTimeUnit()));
            builder.append(",\"mean\":").append(timer.mean(getBaseTimeUnit()));
            builder.append(",\"max\":").append(timer.max(getBaseTimeUnit()));
        });
    }

    // VisibleForTesting
    void writeSummary(OutputStream os, DistributionSummary summary, long wallTime) throws IOException {
        summary.takeSnapshot();
        writeEvent(os, summary, wallTime, builder -> {
            builder.append(",\"count\":").append(summary.count());
            builder.append(",\"sum\":").append(summary.totalAmount());
            builder.append(",\"mean\":").append(summary.mean());
            builder.append(",\"max\":").append(summary.max());
        });
    }

    // VisibleForTesting
    void writeMeter(OutputStream os, Meter meter, long wallTime) throws IOException {
        writeEvent(os, meter, wallTime, builder -> {
            for (Measurement measurement : meter.measure()) {
                builder.append(",\"").append(measurement.getStatistic().getTagValueRepresentation()).append("\":\"").append(measurement.getValue()).append("\"");
            }
        });
    }

    // VisibleForTesting
    void writeEvent(OutputStream os, Meter meter, long wallTime, Consumer<StringBuilder> consumer) throws IOException {
        StringBuilder sb = new StringBuilder();
        String timestamp = FORMATTER.format(Instant.ofEpochMilli(wallTime));
        String name = getConventionName(meter.getId());
        String type = meter.getId().getType().toString().toLowerCase();
        sb.append("{\"timestamp\":\"").append(timestamp).append("\",\"attributes\":{")
            .append("\"name\":\"").append(name).append('"')
            .append(",\"type\":\"").append(type).append('"');

        List<Tag> tags = getConventionTags(meter.getId());
        for (Tag tag : tags) {
            sb.append(",\"").append(tag.getKey()).append("\":\"").append(tag.getValue()).append('"');
        }

        consumer.accept(sb);
        sb.append("}}");

        os.write(sb.toString().getBytes());
    }

    @Override
    @NonNull
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    private HttpURLConnection openConnection(String uri) {
        String host = config.host();
        try {
            URL templateUrl = new URL(host + uri);
            HttpURLConnection connection = (HttpURLConnection) templateUrl.openConnection();
            connection.setRequestMethod(HttpMethod.POST);
            connection.setConnectTimeout((int) config.connectTimeout().toMillis());
            connection.setReadTimeout((int) config.readTimeout().toMillis());
            connection.setUseCaches(false);
            connection.setRequestProperty(HttpHeader.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            connection.setDoOutput(true);

            connection.setRequestProperty(HttpHeader.AUTHORIZATION, "Bearer " + apiToken);

            connection.connect();

            return connection;
        } catch (IOException e) {
            logger.error("Error connecting to {}: {}", host, e);
        }

        return null;
    }

}
