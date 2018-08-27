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
package io.micrometer.elastic;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.HttpHeader;
import io.micrometer.core.instrument.util.HttpMethod;
import io.micrometer.core.instrument.util.IOUtils;
import io.micrometer.core.instrument.util.MediaType;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author Nicolas Portmann
 * @author Jon Schneider
 */
public class ElasticMeterRegistry extends StepMeterRegistry {

    private final Logger logger = LoggerFactory.getLogger(ElasticMeterRegistry.class);

    static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String ES_METRICS_TEMPLATE = "/_template/metrics_template";
    private static final byte[] INDEX_LINE = "{ \"index\" : {} }\n".getBytes(StandardCharsets.UTF_8);

    private final ElasticConfig config;
    private final String authHeader;
    private boolean checkedForIndexTemplate = false;

    public ElasticMeterRegistry(ElasticConfig config, Clock clock, NamingConvention namingConvention, ThreadFactory threadFactory) {
        super(config, clock);
        this.config().namingConvention(namingConvention);
        this.config = config;

        if (StringUtils.isNotBlank(config.userName()) && StringUtils.isNotBlank(config.password())) {
            byte[] authBinary = (config.userName() + ":" + config.password()).getBytes(StandardCharsets.UTF_8);
            String authEncoded = Base64.getEncoder().encodeToString(authBinary);
            this.authHeader = "Basic " + authEncoded;
        } else {
            this.authHeader = null;
        }

        start(threadFactory);
    }

    public ElasticMeterRegistry(ElasticConfig config, Clock clock) {
        this(config, clock, new ElasticNamingConvention(), Executors.defaultThreadFactory());
    }

    private void createIndexIfNeeded() {
        if (!config.autoCreateIndex()) {
            return;
        }
        try {
            HttpURLConnection connection = openConnection(ES_METRICS_TEMPLATE, HttpMethod.HEAD);
            if (connection == null) {
                if (logger.isErrorEnabled()) {
                    logger.error("Could not connect to any configured elasticsearch instances: {}", Arrays.asList(config.hosts()));
                }
                return;
            }
            connection.disconnect();

            boolean isTemplateMissing = connection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND;
            if (!isTemplateMissing) {
                checkedForIndexTemplate = true;
                logger.debug("Metrics template already setup");
                return;
            }

            logger.debug("No metrics template found in elasticsearch. Adding...");
            HttpURLConnection putTemplateConnection = openConnection(ES_METRICS_TEMPLATE, HttpMethod.PUT);
            if (putTemplateConnection == null) {
                logger.error("Error adding metrics template to elasticsearch");
                return;
            }

            try (OutputStream outputStream = putTemplateConnection.getOutputStream()) {
                outputStream.write("{\"template\":\"metrics*\",\"mappings\":{\"_default_\":{\"_all\":{\"enabled\":false},\"properties\":{\"name\":{\"type\":\"keyword\"}}}}}".getBytes());
                outputStream.flush();
                
                if (putTemplateConnection.getResponseCode() != 200) {
                    logger.error("Error adding metrics template to elasticsearch: {}/{}", putTemplateConnection.getResponseCode(), putTemplateConnection.getResponseMessage());
                    return;
                }
            }
            finally {
                putTemplateConnection.disconnect();
            }

            checkedForIndexTemplate = true;
        } catch (IOException ex) {
            logger.error("Error when checking/adding metrics template to elasticsearch", ex);
        }
    }

    @Override
    protected void publish() {
        if (!checkedForIndexTemplate) {
            createIndexIfNeeded();
        }

        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            long wallTime = config().clock().wallTime();

            ZonedDateTime dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wallTime), ZoneOffset.UTC);
            String indexName = config.index() + "-" + DateTimeFormatter.ofPattern(config.indexDateFormat()).format(dt);
            HttpURLConnection connection = openConnection(indexName + "/doc/_bulk", HttpMethod.POST);

            if (connection == null) {
                if (logger.isErrorEnabled()) {
                    logger.error("Could not connect to any configured elasticsearch instances: {}", Arrays.asList(config.hosts()));
                }
                return;
            }

            try (OutputStream outputStream = connection.getOutputStream()) {
                for (Meter m : batch) {
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

                outputStream.flush();

                if (connection.getResponseCode() >= 400) {
                    if (logger.isErrorEnabled()) {
                        try {
                            logger.error("failed to send metrics to elasticsearch (HTTP {}). Cause: {}", connection.getResponseCode(), IOUtils.toString(connection.getErrorStream(), StandardCharsets.UTF_8));
                        } catch (IOException ignored) {
                        }
                    }
                    return; // don't try another batch
                } else {
                    try {
                        // It's not enough to look at response code. ES could return {"errors":true} in body:
                        // {"took":16,"errors":true,"items":[{"index":{"_index":"metrics-2018-03","_type":"timer","_id":"i8kdBmIBmtn9wpUGezjX","status":400,"error":{"type":"illegal_argument_exception","reason":"Rejecting mapping update to [metrics-2018-03] as the final mapping would have more than 1 type: [metric, doc]"}}}]}
                        String response = IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8);
                        if (response.contains("\"errors\":true")) {
                            logger.warn("failed to send metrics to elasticsearch (HTTP {}). Cause: {}", connection.getResponseCode(), response);
                            return;
                        } else {
                            logger.info("successfully sent {} metrics to elasticsearch", batch.size());
                        }
                    } catch (IOException ignored) {
                    }
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
        os.write(INDEX_LINE);
        writeDocument(os, counter, wallTime, builder -> {
            builder.append(",\"count\":").append(counter.count());
        });
    }

    // VisibleForTesting
    void writeCounter(OutputStream os, FunctionCounter counter, long wallTime) throws IOException {
        os.write(INDEX_LINE);
        writeDocument(os, counter, wallTime, builder -> {
            builder.append(",\"count\":").append(counter.count());
        });
    }

    // VisibleForTesting
    void writeGauge(OutputStream os, Gauge gauge, long wallTime) throws IOException {
        Double value = gauge.value();
        if (!value.isNaN()) {
            os.write(INDEX_LINE);
            writeDocument(os, gauge, wallTime, builder -> {
                builder.append(",\"value\":").append(value);
            });
        }
    }

    // VisibleForTesting
    void writeGauge(OutputStream os, TimeGauge gauge, long wallTime) throws IOException {
        Double value = gauge.value();
        if (!value.isNaN()) {
            os.write(INDEX_LINE);
            writeDocument(os, gauge, wallTime, builder -> {
                builder.append(",\"value\":").append(gauge.value(getBaseTimeUnit()));
            });
        }
    }

    // VisibleForTesting
    void writeTimer(OutputStream os, FunctionTimer timer, long wallTime) throws IOException {
        os.write(INDEX_LINE);
        writeDocument(os, timer, wallTime, builder -> {
            builder.append(",\"count\":").append(timer.count());
            builder.append(",\"sum\" :").append(timer.totalTime(getBaseTimeUnit()));
            builder.append(",\"mean\":").append(timer.mean(getBaseTimeUnit()));
        });
    }

    // VisibleForTesting
    void writeLongTaskTimer(OutputStream os, LongTaskTimer timer, long wallTime) throws IOException {
        os.write(INDEX_LINE);
        writeDocument(os, timer, wallTime, builder -> {
            builder.append(",\"activeTasks\":").append(timer.activeTasks());
            builder.append(",\"duration\":").append(timer.duration(getBaseTimeUnit()));
        });
    }

    // VisibleForTesting
    void writeTimer(OutputStream os, Timer timer, long wallTime) throws IOException {
        os.write(INDEX_LINE);
        writeDocument(os, timer, wallTime, builder -> {
            builder.append(",\"count\":").append(timer.count());
            builder.append(",\"sum\":").append(timer.totalTime(getBaseTimeUnit()));
            builder.append(",\"mean\":").append(timer.mean(getBaseTimeUnit()));
            builder.append(",\"max\":").append(timer.max(getBaseTimeUnit()));
        });
    }

    // VisibleForTesting
    void writeSummary(OutputStream os, DistributionSummary summary, long wallTime) throws IOException {
        summary.takeSnapshot();
        os.write(INDEX_LINE);
        writeDocument(os, summary, wallTime, builder -> {
            builder.append(",\"count\":").append(summary.count());
            builder.append(",\"sum\":").append(summary.totalAmount());
            builder.append(",\"mean\":").append(summary.mean());
            builder.append(",\"max\":").append(summary.max());
        });
    }

    // VisibleForTesting
    void writeMeter(OutputStream os, Meter meter, long wallTime) throws IOException {
        os.write(INDEX_LINE);
        writeDocument(os, meter, wallTime, builder -> {
            for (Measurement measurement : meter.measure()) {
                builder.append(",\"").append(measurement.getStatistic().getTagValueRepresentation()).append("\":\"").append(measurement.getValue()).append("\"");
            }
        });
    }

    // VisibleForTesting
    void writeDocument(OutputStream os, Meter meter, long wallTime, Consumer<StringBuilder> consumer) throws IOException {
        StringBuilder sb = new StringBuilder();
        String timestamp = FORMATTER.format(Instant.ofEpochMilli(wallTime));
        String name = getConventionName(meter.getId());
        String type = meter.getId().getType().toString().toLowerCase();
        sb.append("{\"").append(config.timestampFieldName()).append("\":\"").append(timestamp).append('"')
            .append(",\"name\":\"").append(name).append('"')
            .append(",\"type\":\"").append(type).append('"');

        List<Tag> tags = getConventionTags(meter.getId());
        for (Tag tag : tags) {
            sb.append(",\"").append(tag.getKey()).append("\":\"").append(tag.getValue()).append('"');
        }

        consumer.accept(sb);
        sb.append("}\n");

        os.write(sb.toString().getBytes());
    }

    @Override
    @NonNull
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    private HttpURLConnection openConnection(String uri, String method) {
        for (String host : config.hosts()) {
            try {
                URL templateUrl = new URL(host + uri);
                HttpURLConnection connection = (HttpURLConnection) templateUrl.openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout((int) config.connectTimeout().toMillis());
                connection.setReadTimeout((int) config.readTimeout().toMillis());
                connection.setUseCaches(false);
                connection.setRequestProperty(HttpHeader.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                if (method.equalsIgnoreCase(HttpMethod.POST) || method.equalsIgnoreCase(HttpMethod.PUT)) {
                    connection.setDoOutput(true);
                }

                if (this.authHeader != null) {
                    connection.setRequestProperty(HttpHeader.AUTHORIZATION, authHeader);
                }

                connection.connect();

                return connection;
            } catch (IOException e) {
                logger.error("Error connecting to {}: {}", host, e);
            }
        }

        return null;
    }

}
