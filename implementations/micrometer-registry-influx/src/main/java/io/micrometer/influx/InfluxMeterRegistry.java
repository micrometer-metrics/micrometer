/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.*;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static java.util.stream.Collectors.joining;

/**
 * {@link MeterRegistry} for InfluxDB; since Micrometer 1.7, this also support the InfluxDB v2.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class InfluxMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("influx-metrics-publisher");
    private final InfluxConfig config;
    private final HttpSender httpClient;
    private final Logger logger = LoggerFactory.getLogger(InfluxMeterRegistry.class);
    private boolean storageExists = false;
    private InfluxDBVersion influxDBVersion;
    private String org = "";

    @SuppressWarnings("deprecation")
    public InfluxMeterRegistry(InfluxConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    /**
     * @param config        Configuration options for the registry that are describable as properties.
     * @param clock         The clock to use for timings.
     * @param threadFactory The thread factory to use to create the publishing thread.
     * @deprecated Use {@link #builder(InfluxConfig)} instead.
     */
    @Deprecated
    public InfluxMeterRegistry(InfluxConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private InfluxMeterRegistry(InfluxConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        config().namingConvention(new InfluxNamingConvention());
        this.config = config;
        this.httpClient = httpClient;
        start(threadFactory);
    }

    public static Builder builder(InfluxConfig config) {
        return new Builder(config);
    }

    private void createStorageIfNecessary() {
        if (!config.autoCreateDb() || storageExists)
            return;

        switch (influxDBVersion) {
            case V1:
                createDatabaseIfNecessary();
                break;
            case V2:
                createBucketIfNecessary();
                break;
        }
    }

    private void createDatabaseIfNecessary() {

        try {
            String createDatabaseQuery = new CreateDatabaseQueryBuilder(config.db()).setRetentionDuration(config.retentionDuration())
                    .setRetentionPolicyName(config.retentionPolicy())
                    .setRetentionReplicationFactor(config.retentionReplicationFactor())
                    .setRetentionShardDuration(config.retentionShardDuration()).build();

            HttpSender.Request.Builder requestBuilder = httpClient
                    .post(config.uri() + "/query?q=" + URLEncoder.encode(createDatabaseQuery, "UTF-8"))
                    .withBasicAuthentication(config.userName(), config.password());
            influxDBVersion.addHeaderToken(this, requestBuilder);

            requestBuilder
                    .send()
                    .onSuccess(response -> {
                        logger.debug("influx database {} is ready to receive metrics", config.db());
                        storageExists = true;
                    })
                    .onError(response -> logger.error("unable to create database '{}': {}", config.db(), response.body()));
        } catch (Throwable e) {
            logger.error("unable to create database '{}'", config.db(), e);
        }
    }

    private void createBucketIfNecessary() {

        try {
            String createBucketJSON = new CreateBucketJSONBuilder(config.bucket(), org)
                    .setEverySeconds(config.retentionDuration())
                    .build();

            HttpSender.Request.Builder requestBuilder = httpClient
                    .post(config.uri() + "/api/v2/buckets");
            influxDBVersion.addHeaderToken(this, requestBuilder);
            
            requestBuilder
                    .withJsonContent(createBucketJSON)
                    .send()
                    .onSuccess(response -> {
                        logger.debug("influx bucket {} is ready to receive metrics", config.bucket());
                        storageExists = true;
                    })
                    .onError(response -> logger.error("unable to create bucket '{}': {}", config.bucket(), response.body()));
        } catch (Throwable e) {
            logger.warn("unable to create bucket '{}'", config.bucket(), e);
        }
    }

    @Override
    protected void publish() {
        recognizeInfluxDBVersion();
        createStorageIfNecessary();

        try {
            String influxEndpoint = influxDBVersion.writeEndpoint(this);
            if (StringUtils.isNotBlank(config.retentionPolicy())) {
                influxEndpoint += "&rp=" + config.retentionPolicy();
            }

            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                HttpSender.Request.Builder requestBuilder = httpClient
                        .post(influxEndpoint)
                        .withBasicAuthentication(config.userName(), config.password());
                influxDBVersion.addHeaderToken(this, requestBuilder);
                requestBuilder
                        .withPlainText(batch.stream()
                                .flatMap(m -> m.match(
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
                            logger.debug("successfully sent {} metrics to InfluxDB.", batch.size());
                            storageExists = true;
                        })
                        .onError(response -> logger.error("failed to send metrics to influx: {}", response.body()));
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed InfluxDB publishing endpoint, see '" + config.prefix() + ".uri'", e);
        } catch (Throwable e) {
            logger.error("failed to send metrics to influx", e);
        }
    }

    // VisibleForTesting
    Stream<String> writeMeter(Meter m) {
        List<Field> fields = new ArrayList<>();
        for (Measurement measurement : m.measure()) {
            double value = measurement.getValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            String fieldKey = measurement.getStatistic().getTagValueRepresentation()
                    .replaceAll("(.)(\\p{Upper})", "$1_$2").toLowerCase();
            fields.add(new Field(fieldKey, value));
        }
        if (fields.isEmpty()) {
            return Stream.empty();
        }
        Meter.Id id = m.getId();
        return Stream.of(influxLineProtocol(id, id.getType().name().toLowerCase(), fields.stream()));
    }

    private Stream<String> writeLongTaskTimer(LongTaskTimer timer) {
        Stream<Field> fields = Stream.of(
                new Field("active_tasks", timer.activeTasks()),
                new Field("duration", timer.duration(getBaseTimeUnit()))
        );
        return Stream.of(influxLineProtocol(timer.getId(), "long_task_timer", fields));
    }

    // VisibleForTesting
    Stream<String> writeCounter(Meter.Id id, double count) {
        if (Double.isFinite(count)) {
            return Stream.of(influxLineProtocol(id, "counter", Stream.of(new Field("value", count))));
        }
        return Stream.empty();
    }

    // VisibleForTesting
    Stream<String> writeGauge(Meter.Id id, Double value) {
        if (Double.isFinite(value)) {
            return Stream.of(influxLineProtocol(id, "gauge", Stream.of(new Field("value", value))));
        }
        return Stream.empty();
    }

    // VisibleForTesting
    Stream<String> writeFunctionTimer(FunctionTimer timer) {
        double sum = timer.totalTime(getBaseTimeUnit());
        if (Double.isFinite(sum)) {
            Stream.Builder<Field> builder = Stream.builder();
            builder.add(new Field("sum", sum));
            builder.add(new Field("count", timer.count()));
            double mean = timer.mean(getBaseTimeUnit());
            if (Double.isFinite(mean)) {
                builder.add(new Field("mean", mean));
            }
            return Stream.of(influxLineProtocol(timer.getId(), "histogram", builder.build()));
        }
        return Stream.empty();
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
                .filter(t -> StringUtils.isNotBlank(t.getValue()))
                .map(t -> "," + t.getKey() + "=" + t.getValue())
                .collect(joining(""));

        return getConventionName(id)
                + tags + ",metric_type=" + metricType + " "
                + fields.map(Field::toString).collect(joining(","))
                + " " + clock.wallTime();
    }

    private void recognizeInfluxDBVersion() {

        if (influxDBVersion != null) {
            return;
        }

        String uri = config.uri() + "/ping";
        try {
            httpClient
                    .head(uri)
                    .send()
                    .onSuccess(response -> {
                        logger.debug("InfluxDB successfully pinged: '{}'", response.headers());
                        influxDBVersion = response.headers().entrySet()
                                .stream()
                                .filter(entry -> "X-Influxdb-Version".equalsIgnoreCase(entry.getKey()))
                                .map(entry -> {
                                    boolean v1 = entry.getValue().stream().anyMatch(value -> value.startsWith("1."));
                                    return v1 ? InfluxDBVersion.V1 : InfluxDBVersion.V2;
                                })
                                .findFirst()
                                // There is no header "X-Influxdb-Version: 1.X" => use v2 API
                                .orElse(InfluxDBVersion.V2);
                        logger.debug("InfluxDB version configured to: '{}'", influxDBVersion);
                    })
                    .onError(response -> {
                        logger.error("unable to ping InfluxDB: '{}'. Use v2 API.: {}", uri, response.body());
                        // Unable ping http://server/ping => use v2 API
                        influxDBVersion = InfluxDBVersion.V2;
                    });
        } catch (Throwable e) {
            logger.error("unable to ping InfluxDB: '{}', Use v1 API.", uri, e);
            influxDBVersion = InfluxDBVersion.V1;
        }

        if (influxDBVersion.equals(InfluxDBVersion.V2)) {
            org = config.org();
            if (StringUtils.isBlank(org)) {
                retrieveOrg();
            }

            checkAll(config,
                    c -> StepRegistryConfig.validate(c),
                    checkRequired("token", InfluxConfig::token).andThen(Validated::nonBlank),
                    checkRequired("org", (Function<InfluxConfig, String>) it -> org)
                            .andThen(Validated::nonBlank)
            ).orThrow();
        }
    }

    private void retrieveOrg() {
        String uri = config.uri() + "/api/v2/orgs";
        HttpSender.Request.Builder requestBuilder = httpClient
                .get(uri)
                .withBasicAuthentication(config.userName(), config.password());

        influxDBVersion.addHeaderToken(this, requestBuilder);

        try {
            HttpSender.Response response = requestBuilder
                    .acceptJson()
                    .send()
                    .onError(it -> logger.error("unable to get organizations from InfluxDB: '{}'. {}", uri, it.body()));;

            if (response.isSuccessful()) {
                List<Map<String, Object>> orgs = JsonPath.parse(response.body()).read("$.orgs");
                if (orgs.size() == 1) {
                    Object id = orgs.get(0).get("id");
                    if (id != null) {
                        org = id.toString();
                    }
                }
            }
        } catch (Throwable e) {
            logger.error("unable to get organizations from InfluxDB: '{}'.", uri, e);
        }
    }

    @Override
    protected final TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {
        private final InfluxConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(InfluxConfig config) {
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

        public InfluxMeterRegistry build() {
            return new InfluxMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }

    static class Field {
        final String key;
        final double value;

        Field(String key, double value) {
            // `time` cannot be a field key or tag key
            if (key.equals("time")) {
                throw new IllegalArgumentException("'time' is an invalid field key in InfluxDB");
            }
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + "=" + DoubleFormat.decimalOrNan(value);
        }
    }

    private enum InfluxDBVersion {
        V1 {
            @Override
            String writeEndpoint(final InfluxMeterRegistry registry) {
                InfluxConfig config = registry.config;
                return config.uri() + "/write?consistency=" + config.consistency().toString().toLowerCase() + "&precision=ms&db=" + config.db();
            }

            @Override
            void addHeaderToken(final InfluxMeterRegistry registry, final HttpSender.Request.Builder requestBuilder) {
                InfluxConfig config = registry.config;
                if (config.token() != null) {
                    requestBuilder.withHeader("Authorization", "Bearer " + config.token());
                }
            }
        },
        V2 {
            @Override
            String writeEndpoint(final InfluxMeterRegistry registry) throws UnsupportedEncodingException {
                InfluxConfig config = registry.config;
                return config.uri() + "/api/v2/write?&precision=ms&bucket=" + URLEncoder.encode(config.bucket(), "UTF-8") + "&org=" + URLEncoder.encode(registry.org, "UTF-8");
            }

            @Override
            void addHeaderToken(final InfluxMeterRegistry registry, final HttpSender.Request.Builder requestBuilder) {
                InfluxConfig config = registry.config;
                if (config.token() != null) {
                    requestBuilder.withHeader("Authorization", "Token " + config.token());
                }
            }
        };

        abstract String writeEndpoint(final InfluxMeterRegistry registry) throws UnsupportedEncodingException;

        abstract void addHeaderToken(final InfluxMeterRegistry registry, final HttpSender.Request.Builder requestBuilder);
    }
}
