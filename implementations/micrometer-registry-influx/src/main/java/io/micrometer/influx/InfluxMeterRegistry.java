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
package io.micrometer.influx;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.ipc.http.HttpClient;
import io.micrometer.core.ipc.http.HttpUrlConnectionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.Meter.Type.match;
import static java.util.stream.Collectors.joining;

/**
 * @author Jon Schneider
 */
public class InfluxMeterRegistry extends StepMeterRegistry {
    private final InfluxConfig config;
    private final HttpClient httpClient;
    private final Logger logger = LoggerFactory.getLogger(InfluxMeterRegistry.class);
    private boolean databaseExists = false;

    public InfluxMeterRegistry(InfluxConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionClient(config.connectTimeout(), config.readTimeout()));
    }

    public InfluxMeterRegistry(InfluxConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    private InfluxMeterRegistry(InfluxConfig config, Clock clock, ThreadFactory threadFactory, HttpClient httpClient) {
        super(config, clock);
        this.config().namingConvention(new InfluxNamingConvention());
        this.config = config;
        this.httpClient = httpClient;
        start(threadFactory);
    }

    private void createDatabaseIfNecessary() {
        if (!config.autoCreateDb() || databaseExists)
            return;

        try {
            String createDatabaseQuery = new CreateDatabaseQueryBuilder(config.db()).setRetentionDuration(config.retentionDuration())
                    .setRetentionPolicyName(config.retentionPolicy())
                    .setRetentionReplicationFactor(config.retentionReplicationFactor())
                    .setRetentionShardDuration(config.retentionShardDuration()).build();

            httpClient
                    .post(config.uri() + "/query?q=" + URLEncoder.encode(createDatabaseQuery, "UTF-8"))
                    .withBasicAuthentication(config.userName(), config.password())
                    .send()
                    .onSuccess(response -> {
                        logger.debug("influx database {} is ready to receive metrics", config.db());
                        databaseExists = true;
                    })
                    .onError(response -> logger.error("unable to create database '{}': {}", config.db(), response.body()));
        } catch (Throwable e) {
            logger.error("unable to create database '{}'", config.db(), e);
        }
    }

    @Override
    protected void publish() {
        createDatabaseIfNecessary();

        try {
            String influxEndpoint = config.uri() + "/write?consistency=" + config.consistency().toString().toLowerCase() + "&precision=ms&db=" + config.db();
            if (StringUtils.isNotBlank(config.retentionPolicy())) {
                influxEndpoint += "&rp=" + config.retentionPolicy();
            }

            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                httpClient.post(influxEndpoint)
                        .withBasicAuthentication(config.userName(), config.password())
                        .withPlainText(batch.stream()
                                .flatMap(m -> match(m,
                                        gauge -> writeGauge(gauge.getId(), gauge.value()),
                                        counter -> writeCounter(counter.getId(), counter.count()),
                                        this::writeTimer,
                                        this::writeSummary,
                                        this::writeLongTaskTimer,
                                        gauge -> writeGauge(gauge.getId(), gauge.value(getBaseTimeUnit())),
                                        counter -> writeCounter(counter.getId(), counter.count()),
                                        this::writeFunctionTimer,
                                        this::writeMeter))
                                .collect(joining("\n")))
                        .compressWhen(config::compressed)
                        .send()
                        .onSuccess(response -> {
                            logger.debug("Successfully sent {} metrics to InfluxDB.", batch.size());
                            databaseExists = true;
                        })
                        .onError(response -> logger.error("failed to send metrics to influx: {}", response.body()));
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed InfluxDB publishing endpoint, see '" + config.prefix() + ".uri'", e);
        } catch (Throwable e) {
            logger.error("failed to send metrics to influx", e);
        }
    }

    class Field {
        final String key;
        final double value;

        Field(String key, double value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + "=" + DoubleFormat.decimalOrNan(value);
        }
    }

    private Stream<String> writeMeter(Meter m) {
        Stream.Builder<Field> fields = Stream.builder();

        for (Measurement measurement : m.measure()) {
            String fieldKey = measurement.getStatistic().toString()
                    .replaceAll("(.)(\\p{Upper})", "$1_$2").toLowerCase();
            fields.add(new Field(fieldKey, measurement.getValue()));
        }

        return Stream.of(influxLineProtocol(m.getId(), "unknown", fields.build()));
    }

    private Stream<String> writeLongTaskTimer(LongTaskTimer timer) {
        Stream<Field> fields = Stream.of(
                new Field("active_tasks", timer.activeTasks()),
                new Field("duration", timer.duration(getBaseTimeUnit()))
        );
        return Stream.of(influxLineProtocol(timer.getId(), "long_task_timer", fields));
    }

    private Stream<String> writeCounter(Meter.Id id, double count) {
        return Stream.of(influxLineProtocol(id, "counter", Stream.of(new Field("value", count))));
    }

    private Stream<String> writeGauge(Meter.Id id, Double value) {
        return value.isNaN() ? Stream.empty() :
                Stream.of(influxLineProtocol(id, "gauge", Stream.of(new Field("value", value))));
    }

    private Stream<String> writeFunctionTimer(FunctionTimer timer) {
        Stream<Field> fields = Stream.of(
                new Field("sum", timer.totalTime(getBaseTimeUnit())),
                new Field("count", timer.count()),
                new Field("mean", timer.mean(getBaseTimeUnit()))
        );

        return Stream.of(influxLineProtocol(timer.getId(), "histogram", fields));
    }

    private Stream<String> writeTimer(Timer timer) {
        final Stream<Field> fields = Stream.of(
                new Field("sum", timer.totalTime(getBaseTimeUnit())),
                new Field("count", timer.count()),
                new Field("mean", timer.mean(getBaseTimeUnit())),
                new Field("upper", timer.max(getBaseTimeUnit()))
        );

        return Stream.of(influxLineProtocol(timer.getId(), "histogram", fields));
    }

    private Stream<String> writeSummary(DistributionSummary summary) {
        final Stream<Field> fields = Stream.of(
                new Field("sum", summary.totalAmount()),
                new Field("count", summary.count()),
                new Field("mean", summary.mean()),
                new Field("upper", summary.max())
        );

        return Stream.of(influxLineProtocol(summary.getId(), "histogram", fields));
    }

    private String influxLineProtocol(Meter.Id id, String metricType, Stream<Field> fields) {
        String tags = getConventionTags(id).stream()
                .map(t -> "," + t.getKey() + "=" + t.getValue())
                .collect(joining(""));

        return getConventionName(id)
                + tags + ",metric_type=" + metricType + " "
                + fields.map(Field::toString).collect(joining(","))
                + " " + clock.wallTime();
    }

    @Override
    protected final TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {
        private final InfluxConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = Executors.defaultThreadFactory();
        private HttpClient httpClient;

        public Builder(InfluxConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionClient(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public InfluxMeterRegistry build() {
            return new InfluxMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}
