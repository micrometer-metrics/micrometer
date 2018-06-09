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
import io.micrometer.core.instrument.util.DoubleFormat;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Nicolas Portmann
 * @author Jon Schneider
 */
public class ElasticMeterRegistry extends StepMeterRegistry {

    private final Logger logger = LoggerFactory.getLogger(ElasticMeterRegistry.class);

    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    private static final String ES_METRICS_TEMPLATE = "/_template/metrics_template";
    
    private final ElasticConfig config;
    private boolean checkedForIndexTemplate = false;

    public ElasticMeterRegistry(ElasticConfig config, Clock clock, NamingConvention namingConvention, ThreadFactory threadFactory) {
        super(config, clock);
        this.config().namingConvention(namingConvention);
        this.config = config;

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

            String bulkPayload = batch.stream().flatMap(m -> {
                if (m instanceof TimeGauge) {
                    return writeGauge((TimeGauge) m, wallTime);
                } else if (m instanceof Gauge) {
                    return writeGauge((Gauge) m, wallTime);
                } else if (m instanceof Counter) {
                    return writeCounter((Counter) m, wallTime);
                } else if (m instanceof FunctionCounter) {
                    return writeCounter((FunctionCounter) m, wallTime);
                } else if (m instanceof Timer) {
                    return writeTimer((Timer) m, wallTime);
                } else if (m instanceof FunctionTimer) {
                    return writeTimer((FunctionTimer) m, wallTime);
                } else if (m instanceof DistributionSummary) {
                    return writeSummary((DistributionSummary) m, wallTime);
                } else if (m instanceof LongTaskTimer) {
                    return writeLongTaskTimer((LongTaskTimer) m, wallTime);
                } else {
                    return writeMeter(m, wallTime);
                }
            }).collect(Collectors.joining("\n")) + "\n";

            HttpURLConnection connection = openConnection("/_bulk", HttpMethod.POST);
            if (connection == null) {
                if (logger.isErrorEnabled()) {
                    logger.error("Could not connect to any configured elasticsearch instances: {}", Arrays.asList(config.hosts()));
                }
                return;
            }

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bulkPayload.getBytes());
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
    Stream<String> writeCounter(Counter counter, long wallTime) {
        return Stream.of(index(counter, wallTime).field("count", counter.count()).build());
    }

    private Stream<String> writeCounter(FunctionCounter counter, long wallTime) {
        return Stream.of(index(counter, wallTime).field("count", counter.count()).build());
    }

    // VisibleForTesting
    Stream<String> writeGauge(Gauge gauge, long wallTime) {
        Double value = gauge.value();
        return value.isNaN() ? Stream.empty() : Stream.of(index(gauge, wallTime).field("value", value).build());
    }

    // VisibleForTesting
    Stream<String> writeGauge(TimeGauge gauge, long wallTime) {
        Double value = gauge.value();
        return value.isNaN() ? Stream.empty() : Stream.of(index(gauge, wallTime).field("value", gauge.value(getBaseTimeUnit())).build());
    }

    private Stream<String> writeTimer(FunctionTimer timer, long wallTime) {
        return Stream.of(index(timer, wallTime)
                .field("count", timer.count())
                .field("sum", timer.totalTime(getBaseTimeUnit()))
                .field("mean", timer.mean(getBaseTimeUnit()))
                .build());
    }

    private Stream<String> writeLongTaskTimer(LongTaskTimer timer, long wallTime) {
        return Stream.of(index(timer, wallTime)
                .field("activeTasks", timer.activeTasks())
                .field("duration", timer.duration(getBaseTimeUnit()))
                .build());
    }

    private Stream<String> writeTimer(Timer timer, long wallTime) {
        Stream.Builder<String> stream = Stream.builder();
        stream.add(index(timer, wallTime)
                .field("count", timer.count())
                .field("sum", timer.totalTime(getBaseTimeUnit()))
                .field("mean", timer.mean(getBaseTimeUnit()))
                .field("max", timer.max(getBaseTimeUnit()))
                .build());

        return stream.build();
    }

    private Stream<String> writeSummary(DistributionSummary summary, long wallTime) {
        summary.takeSnapshot();
        Stream.Builder<String> stream = Stream.builder();
        stream.add(index(summary, wallTime)
                .field("count", summary.count())
                .field("sum", summary.totalAmount())
                .field("mean", summary.mean())
                .field("max", summary.max())
                .build());

        return stream.build();
    }

    private Stream<String> writeMeter(Meter meter, long wallTime) {
        IndexBuilder index = index(meter, wallTime);
        for (Measurement measurement : meter.measure()) {
            index.field(measurement.getStatistic().getTagValueRepresentation(), measurement.getValue());
        }
        return Stream.of(index.build());
    }

    private IndexBuilder index(Meter meter, long wallTime) {
        return new IndexBuilder(config, getConventionName(meter.getId()), meter.getId().getType().toString().toLowerCase(), getConventionTags(meter.getId()), wallTime);
    }

    // VisibleForTesting
    IndexBuilder index(String name, String type, long wallTime) {
        return new IndexBuilder(config, name, type, Collections.emptyList(), wallTime);
    }

    static class IndexBuilder {
        private StringBuilder indexLine = new StringBuilder(100);

        private IndexBuilder(ElasticConfig config, String name, String type, List<Tag> tags, long wallTime) {
            indexLine.append(indexLine(config, wallTime))
                    .append("{\"").append(config.timestampFieldName()).append("\":\"").append(timestamp(wallTime)).append('"')
                    .append(",\"name\":\"").append(name).append('"')
                    .append(",\"type\":\"").append(type).append('"');

            for (Tag tag : tags) {
                field(tag.getKey(), tag.getValue());
            }
        }

        IndexBuilder field(String name, double value) {
            indexLine.append(",\"").append(name).append("\":").append(DoubleFormat.decimal(value));
            return this;
        }

        IndexBuilder field(String name, String value) {
            indexLine.append(",\"").append(name).append("\":\"").append(value).append('"');
            return this;
        }

        // VisibleForTesting
        static String timestamp(long wallTime) {
            return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(wallTime));
        }

        private static String indexLine(ElasticConfig config, long wallTime) {
            ZonedDateTime dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(wallTime), UTC_ZONE);
            String indexName = config.index() + "-" + DateTimeFormatter.ofPattern(config.indexDateFormat()).format(dt);
            return "{\"index\":{\"_index\":\"" + indexName + "\",\"_type\":\"doc\"}}\n";
        }

        String build() {
            return indexLine.append('}').toString();
        }
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

                if (StringUtils.isNotBlank(config.userName()) && StringUtils.isNotBlank(config.password())) {
                    byte[] authBinary = (config.userName() + ":" + config.password()).getBytes(StandardCharsets.UTF_8);
                    String authEncoded = Base64.getEncoder().encodeToString(authBinary);
                    connection.setRequestProperty(HttpHeader.AUTHORIZATION, "Basic " + authEncoded);
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
