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
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
import static java.util.stream.Collectors.joining;

/**
 * {@link MeterRegistry} for Elasticsearch.
 *
 * @author Nicolas Portmann
 * @author Jon Schneider
 * @since 1.1.0
 */
public class ElasticMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("elastic-metrics-publisher");
    static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final String ES_METRICS_TEMPLATE = "/_template/metrics_template";
    private static final String INDEX_LINE = "{ \"index\" : {} }\n";

    private final Logger logger = LoggerFactory.getLogger(ElasticMeterRegistry.class);

    private final ElasticConfig config;
    private final HttpSender httpClient;

    private final DateTimeFormatter indexDateFormatter;

    private volatile boolean checkedForIndexTemplate = false;

    @SuppressWarnings("deprecation")
    public ElasticMeterRegistry(ElasticConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private ElasticMeterRegistry(ElasticConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        config().namingConvention(new ElasticNamingConvention());
        this.config = config;
        indexDateFormatter = DateTimeFormatter.ofPattern(config.indexDateFormat());
        this.httpClient = httpClient;
        start(threadFactory);
    }

    public static Builder builder(ElasticConfig config) {
        return new Builder(config);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to elastic every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    private void createIndexIfNeeded() {
        if (checkedForIndexTemplate || !config.autoCreateIndex()) {
            return;
        }

        try {
            if (httpClient
                    .head(config.host() + ES_METRICS_TEMPLATE)
                    .withBasicAuthentication(config.userName(), config.password())
                    .send()
                    .onError(response -> {
                        if (response.code() != 404) {
                            logger.error("could not create index in elastic (HTTP {}): {}", response.code(), response.body());
                        }
                    })
                    .isSuccessful()) {
                checkedForIndexTemplate = true;
                logger.debug("metrics template already exists");
                return;
            }

            httpClient.put(config.host() + ES_METRICS_TEMPLATE)
                    .withBasicAuthentication(config.userName(), config.password())
                    .withJsonContent("{\"template\":\"metrics*\",\"mappings\":{\"_default_\":{\"_all\":{\"enabled\":false},\"properties\":{\"name\":{\"type\":\"keyword\"}}}}}")
                    .send()
                    .onError(response -> logger.error("failed to add metrics template to elastic: {}", response.body()));
        } catch (Throwable e) {
            logger.error("could not create index in elastic", e);
            return;
        }

        checkedForIndexTemplate = true;
    }

    @Override
    protected void publish() {
        createIndexIfNeeded();

        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            try {
                httpClient
                        .post(config.host() + "/" + indexName() + "/doc/_bulk")
                        .withBasicAuthentication(config.userName(), config.password())
                        .withJsonContent(batch.stream()
                                .map(m -> m.match(
                                        this::writeGauge,
                                        this::writeCounter,
                                        this::writeTimer,
                                        this::writeSummary,
                                        this::writeLongTaskTimer,
                                        this::writeTimeGauge,
                                        this::writeFunctionCounter,
                                        this::writeFunctionTimer,
                                        this::writeMeter))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(joining("\n", "", "\n")))
                        .send()
                        .onSuccess(response -> {
                            // It's not enough to look at response code. ES could return {"errors":true} in body:
                            // {"took":16,"errors":true,"items":[{"index":{"_index":"metrics-2018-03","_type":"timer","_id":"i8kdBmIBmtn9wpUGezjX","status":400,"error":{"type":"illegal_argument_exception","reason":"Rejecting mapping update to [metrics-2018-03] as the final mapping would have more than 1 type: [metric, doc]"}}}]}
                            String body = response.body();
                            if (body.contains("\"errors\":true")) {
                                logger.error("failed to send metrics to elastic: {}", body);
                            } else {
                                logger.debug("successfully sent {} metrics to elastic", batch.size());
                            }
                        })
                        .onError(response -> logger.error("failed to send metrics to elastic: {}", response.body()));
            } catch (Throwable e) {
                logger.error("failed to send metrics to elastic", e);
            }
        }
    }

    private String indexName() {
        ZonedDateTime dt = ZonedDateTime.ofInstant(new Date(config().clock().wallTime()).toInstant(), ZoneOffset.UTC);
        return config.index() + "-" + indexDateFormatter.format(dt);
    }

    // VisibleForTesting
    Optional<String> writeCounter(Counter counter) {
        return writeCounter(counter, counter.count());
    }

    // VisibleForTesting
    Optional<String> writeFunctionCounter(FunctionCounter counter) {
        return writeCounter(counter, counter.count());
    }

    private Optional<String> writeCounter(Meter meter, Double value) {
        if (Double.isFinite(value)) {
            return Optional.of(writeDocument(meter, builder -> {
                builder.append(",\"count\":").append(value);
            }));
        }
        return Optional.empty();
    }

    // VisibleForTesting
    Optional<String> writeGauge(Gauge gauge) {
        Double value = gauge.value();
        if (Double.isFinite(value)) {
            return Optional.of(writeDocument(gauge, builder -> {
                builder.append(",\"value\":").append(value);
            }));
        }
        return Optional.empty();
    }

    // VisibleForTesting
    Optional<String> writeTimeGauge(TimeGauge gauge) {
        Double value = gauge.value(getBaseTimeUnit());
        if (Double.isFinite(value)) {
            return Optional.of(writeDocument(gauge, builder -> {
                builder.append(",\"value\":").append(value);
            }));
        }
        return Optional.empty();
    }

    // VisibleForTesting
    Optional<String> writeFunctionTimer(FunctionTimer timer) {
        return Optional.of(writeDocument(timer, builder -> {
            builder.append(",\"count\":").append(timer.count());
            builder.append(",\"sum\" :").append(timer.totalTime(getBaseTimeUnit()));
            builder.append(",\"mean\":").append(timer.mean(getBaseTimeUnit()));
        }));
    }

    // VisibleForTesting
    Optional<String> writeLongTaskTimer(LongTaskTimer timer) {
        return Optional.of(writeDocument(timer, builder -> {
            builder.append(",\"activeTasks\":").append(timer.activeTasks());
            builder.append(",\"duration\":").append(timer.duration(getBaseTimeUnit()));
        }));
    }

    // VisibleForTesting
    Optional<String> writeTimer(Timer timer) {
        return Optional.of(writeDocument(timer, builder -> {
            builder.append(",\"count\":").append(timer.count());
            builder.append(",\"sum\":").append(timer.totalTime(getBaseTimeUnit()));
            builder.append(",\"mean\":").append(timer.mean(getBaseTimeUnit()));
            builder.append(",\"max\":").append(timer.max(getBaseTimeUnit()));
        }));
    }

    // VisibleForTesting
    Optional<String> writeSummary(DistributionSummary summary) {
        summary.takeSnapshot();
        return Optional.of(writeDocument(summary, builder -> {
            builder.append(",\"count\":").append(summary.count());
            builder.append(",\"sum\":").append(summary.totalAmount());
            builder.append(",\"mean\":").append(summary.mean());
            builder.append(",\"max\":").append(summary.max());
        }));
    }

    // VisibleForTesting
    Optional<String> writeMeter(Meter meter) {
        return Optional.of(writeDocument(meter, builder -> {
            for (Measurement measurement : meter.measure()) {
                builder.append(",\"").append(measurement.getStatistic().getTagValueRepresentation()).append("\":\"").append(measurement.getValue()).append("\"");
            }
        }));
    }

    // VisibleForTesting
    String writeDocument(Meter meter, Consumer<StringBuilder> consumer) {
        StringBuilder sb = new StringBuilder(INDEX_LINE);
        String timestamp = TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(config().clock().wallTime()));
        String name = getConventionName(meter.getId());
        String type = meter.getId().getType().toString().toLowerCase();
        sb.append("{\"").append(config.timestampFieldName()).append("\":\"").append(timestamp).append('"')
                .append(",\"name\":\"").append(escapeJson(name)).append('"')
                .append(",\"type\":\"").append(type).append('"');

        List<Tag> tags = getConventionTags(meter.getId());
        for (Tag tag : tags) {
            sb.append(",\"").append(escapeJson(tag.getKey())).append("\":\"")
                    .append(escapeJson(tag.getValue())).append('"');
        }

        consumer.accept(sb);
        sb.append("}");

        return sb.toString();
    }

    @Override
    @NonNull
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {
        private final ElasticConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(ElasticConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpSender httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public ElasticMeterRegistry build() {
            return new ElasticMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}
