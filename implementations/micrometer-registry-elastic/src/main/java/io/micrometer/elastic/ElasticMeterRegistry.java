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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.NonNull;
import io.micrometer.elastic.ElasticMetricsModule.BulkIndexOperationHeader;
import io.micrometer.elastic.ElasticSerializableMeters.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Nicolas Portmann
 */
public class ElasticMeterRegistry extends StepMeterRegistry {
    private final Logger logger = LoggerFactory.getLogger(ElasticMeterRegistry.class);

    private final ElasticConfig config;
    private String currentIndexName;
    private final SimpleDateFormat indexDateFormat;
    private boolean checkedForIndexTemplate = false;
    private final ObjectWriter objectWriter;

    public ElasticMeterRegistry(ElasticConfig config, Clock clock, NamingConvention namingConvention, ThreadFactory threadFactory) {
        super(config, clock);
        this.config().namingConvention(namingConvention);
        this.config = config;
        this.indexDateFormat = new SimpleDateFormat(config.indexDateFormat());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(SerializationFeature.CLOSE_CLOSEABLE, false);
        objectMapper.configure(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT, false);
        objectMapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        objectMapper.registerModule(new AfterburnerModule());
        objectMapper.registerModule(new ElasticMetricsModule(
            config.rateUnits(),
            config.durationUnits(),
            config.timeStampFieldName(),
            config.metricPrefix())
        );
        objectWriter = objectMapper.writer();

        start(threadFactory);

        logger.info("ElasticMeterRegistry started");
    }

    public ElasticMeterRegistry(ElasticConfig config, Clock clock) {
        this(config, clock, NamingConvention.snakeCase, Executors.defaultThreadFactory());
    }

    private void createIndexIfNeeded() {
        if (!config.autoCreateIndex()) {
            return;
        }
        try {
            HttpURLConnection connection = openConnection( "/_template/metrics_template", "HEAD");
            if (connection == null) {
                logger.error("Could not connect to any configured elasticsearch instances: {}", Arrays.asList(config.hosts()));
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
            HttpURLConnection putTemplateConnection = openConnection( "/_template/metrics_template", "PUT");
            if(putTemplateConnection == null) {
                logger.error("Error adding metrics template to elasticsearch");
                return;
            }

            JsonGenerator json = new JsonFactory().createGenerator(putTemplateConnection.getOutputStream());
            json.writeStartObject();
            json.writeStringField("template", config.index() + "*");
            json.writeObjectFieldStart("mappings");

            json.writeObjectFieldStart("_default_");
            json.writeObjectFieldStart("_all");
            json.writeBooleanField("enabled", false);
            json.writeEndObject();
            json.writeObjectFieldStart("properties");
            json.writeObjectFieldStart("name");
            json.writeObjectField("type", "string");
            json.writeObjectField("index", "not_analyzed");
            json.writeEndObject();
            json.writeEndObject();
            json.writeEndObject();

            json.writeEndObject();
            json.writeEndObject();
            json.flush();

            putTemplateConnection.disconnect();
            if (putTemplateConnection.getResponseCode() != 200) {
                logger.error("Error adding metrics template to elasticsearch: {}/{}" + putTemplateConnection.getResponseCode(), putTemplateConnection.getResponseMessage());
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

        final long timestamp = clock.wallTime();

        currentIndexName = config.index() + "-" + indexDateFormat.format(new Date(timestamp));

        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            HttpURLConnection connection = openConnection("/_bulk", "POST");
            if (connection == null) {
                logger.error("Could not connect to any configured elasticsearch instances: {}", Arrays.asList(config.hosts()));
                return;
            }

            String body = batch.stream().map(m -> {
                if (m instanceof Timer) {
                    return new ElasticTimer((Timer) m, clock.wallTime());
                }
                if (m instanceof DistributionSummary) {
                    return new ElasticDistributionSummary((DistributionSummary) m, clock.wallTime());
                }
                if (m instanceof FunctionTimer) {
                    return new ElasticFunctionTimer((FunctionTimer) m, clock.wallTime());
                }
                if (m instanceof TimeGauge) {
                    return new ElasticTimeGauge((TimeGauge) m,  clock.wallTime());
                }
                if (m instanceof Gauge) {
                    return new ElasticGauge((Gauge) m, clock.wallTime());
                }
                if (m instanceof FunctionCounter) {
                    return new ElasticFunctionCounter((FunctionCounter) m, clock.wallTime());
                }
                if (m instanceof Counter) {
                    return new ElasticCounter((Counter) m, clock.wallTime());
                }
                if (m instanceof LongTaskTimer) {
                    return new ElasticSerializableMeters.ElasticLongTaskTimer((LongTaskTimer) m, clock.wallTime());
                }
                return new ElasticSerializableMeters.ElasticMeter(m, clock.wallTime());
            }).map(m -> {
                BulkIndexOperationHeader header = new BulkIndexOperationHeader(currentIndexName, m.getType());

                try {
                    return objectWriter.writeValueAsString(header) + "\n" + objectWriter.writeValueAsString(m) + "\n";
                } catch (JsonProcessingException e) {
                    logger.error("Could not serialize meter", e);
                    return "";
                }

            }).collect(Collectors.joining("\n", "", "\n"));

            writeAndCloseConnection(body, connection);
        }

        logger.debug("Reported meters to elasticsearch");
    }

    @Override
    @NonNull
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    private HttpURLConnection openConnection(String uri, String method) {
        for (String host : config.hosts()) {
            try {
                URL templateUrl = new URL(host  + uri);
                HttpURLConnection connection = ( HttpURLConnection ) templateUrl.openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(config.timeout());
                connection.setUseCaches(false);
                connection.setRequestProperty("Content-Type", "application/json");
                if (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT")) {
                    connection.setDoOutput(true);
                }

                if (!"".equals(config.userName()) && !"".equals(config.password())) {
                    byte[] authBinary = (config.userName()+":"+config.password()).getBytes(StandardCharsets.UTF_8);
                    String authEncoded = Base64.getEncoder().encodeToString(authBinary);
                    connection.setRequestProperty("Authorization", "Basic " + authEncoded);
                }

                connection.connect();

                return connection;
            } catch (IOException e) {
                logger.error("Error connecting to {}: {}", host, e);
            }
        }

        return null;
    }

    private void writeAndCloseConnection(String body, HttpURLConnection connection) {
        try {
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(body.getBytes());
            outputStream.flush();
            outputStream.close();

            connection.disconnect();

            if (connection.getResponseCode() != 200) {
                logger.error("Reporting returned code {} {}: {}", connection.getResponseCode(), connection.getResponseMessage());
            }
        } catch (IOException e) {
            logger.error("Couldn't write to elasticsearch server", e);
        }
    }
}
