/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.appoptics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static io.micrometer.core.instrument.util.DoubleFormat.decimal;
import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
import static java.util.stream.Collectors.joining;

/**
 * Publishes metrics to AppOptics.
 *
 * @author Hunter Sherman
 * @author Jon Schneider
 * @author Johnny Lim
 * @since 1.1.0
 */
public class AppOpticsMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("appoptics-metrics-publisher");
    private static final String BODY_MEASUREMENTS_PREFIX = "{\"measurements\":[";
    private static final String BODY_MEASUREMENTS_SUFFIX = "]}";
    private static final String BODY_MEASUREMENTS_NONE = BODY_MEASUREMENTS_PREFIX + BODY_MEASUREMENTS_SUFFIX;

    private final Logger logger = LoggerFactory.getLogger(AppOpticsMeterRegistry.class);

    private final AppOpticsConfig config;
    private final HttpSender httpClient;

    @SuppressWarnings("deprecation")
    public AppOpticsMeterRegistry(AppOpticsConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private AppOpticsMeterRegistry(AppOpticsConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);

        config().namingConvention(new AppOpticsNamingConvention());

        this.config = config;
        this.httpClient = httpClient;

        config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (id.getName().startsWith("system.")) {
                    return id.withName("micrometer." + id.getName());
                }
                return id;
            }
        });

        start(threadFactory);
    }

    public static Builder builder(AppOpticsConfig config) {
        return new Builder(config);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to appoptics every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        try {
            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                String body = batch.stream()
                        .map(meter -> meter.match(
                                this::writeGauge,
                                this::writeCounter,
                                this::writeTimer,
                                this::writeSummary,
                                this::writeLongTaskTimer,
                                this::writeTimeGauge,
                                this::writeFunctionCounter,
                                this::writeFunctionTimer,
                                this::writeMeter)
                        )
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(joining(",", BODY_MEASUREMENTS_PREFIX, BODY_MEASUREMENTS_SUFFIX));
                if (body.equals(BODY_MEASUREMENTS_NONE)) {
                    continue;
                }
                httpClient.post(config.uri())
                        .withBasicAuthentication(config.apiToken(), "")
                        .withJsonContent(body)
                        .send()
                        .onSuccess(response -> {
                            if (!response.body().contains("\"failed\":0")) {
                                logger.error("failed to send at least some metrics to appoptics: {}", response.body());
                            } else {
                                logger.debug("successfully sent {} metrics to appoptics", batch.size());
                            }
                        })
                        .onError(response -> logger.error("failed to send metrics to appoptics: {}", response.body()));
            }
        } catch (Throwable t) {
            logger.warn("failed to send metrics to appoptics", t);
        }
    }

    private Optional<String> writeMeter(Meter meter) {
        return Optional.of(StreamSupport.stream(meter.measure().spliterator(), false)
                .map(ms -> write(meter.getId().withTag(ms.getStatistic()), null, Fields.Value.tag(), decimal(ms.getValue())))
                .collect(joining(",")));
    }

    // VisibleForTesting
    Optional<String> writeGauge(Gauge gauge) {
        double value = gauge.value();
        if (!Double.isFinite(value)) {
            return Optional.empty();
        }
        return Optional.of(write(gauge.getId(), "gauge", Fields.Value.tag(), decimal(value)));
    }

    // VisibleForTesting
    Optional<String> writeTimeGauge(TimeGauge timeGauge) {
        double value = timeGauge.value(getBaseTimeUnit());
        if (!Double.isFinite(value)) {
            return Optional.empty();
        }
        return Optional.of(write(timeGauge.getId(), "timeGauge", Fields.Value.tag(), decimal(value)));
    }

    @Nullable
    private Optional<String> writeCounter(Counter counter) {
        double count = counter.count();
        if (count > 0) {
            // can't use "count" field because sum is required whenever count is set.
            return Optional.of(write(counter.getId(), "counter", Fields.Value.tag(), decimal(count)));
        }
        return Optional.empty();
    }

    // VisibleForTesting
    @Nullable
    Optional<String> writeFunctionCounter(FunctionCounter counter) {
        double count = counter.count();
        if (Double.isFinite(count) && count > 0) {
            // can't use "count" field because sum is required whenever count is set.
            return Optional.of(write(counter.getId(), "functionCounter", Fields.Value.tag(), decimal(count)));
        }
        return Optional.empty();
    }

    @Nullable
    private Optional<String> writeFunctionTimer(FunctionTimer timer) {
        double count = timer.count();
        if (count > 0) {
            return Optional.of(write(timer.getId(), "functionTimer",
                    Fields.Count.tag(), decimal(count),
                    Fields.Sum.tag(), decimal(timer.totalTime(getBaseTimeUnit()))));
        }
        return Optional.empty();
    }

    @Nullable
    private Optional<String> writeTimer(Timer timer) {
        HistogramSnapshot snapshot = timer.takeSnapshot();
        long count = snapshot.count();
        if (count > 0) {
            return Optional.of(write(timer.getId(), "timer",
                    Fields.Count.tag(), decimal(count),
                    Fields.Sum.tag(), decimal(snapshot.total(getBaseTimeUnit())),
                    Fields.Max.tag(), decimal(snapshot.max(getBaseTimeUnit()))));
        }
        return Optional.empty();
    }

    @Nullable
    private Optional<String> writeSummary(DistributionSummary summary) {
        HistogramSnapshot snapshot = summary.takeSnapshot();
        if (snapshot.count() > 0) {
            return Optional.of(write(summary.getId(), "distributionSummary",
                    Fields.Count.tag(), decimal(summary.count()),
                    Fields.Sum.tag(), decimal(summary.totalAmount()),
                    Fields.Max.tag(), decimal(summary.max())));
        }
        return Optional.empty();
    }

    @Nullable
    private Optional<String> writeLongTaskTimer(LongTaskTimer timer) {
        int activeTasks = timer.activeTasks();
        if (activeTasks > 0) {
            return Optional.of(write(timer.getId(), "longTaskTimer",
                    Fields.Count.tag(), decimal(activeTasks),
                    Fields.Sum.tag(), decimal(timer.duration(getBaseTimeUnit()))));
        }
        return Optional.empty();
    }

    private String write(Meter.Id id, @Nullable String type, String... statistics) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":\"").append(escapeJson(getConventionName(id))).append("\",\"period\":").append(config.step().getSeconds());

        if (!Fields.Value.tag().equals(statistics[0])) {
            sb.append(",\"attributes\":{\"aggregate\":false}");
        }

        for (int i = 0; i < statistics.length; i += 2) {
            sb.append(",\"").append(statistics[i]).append("\":").append(statistics[i + 1]);
        }

        List<Tag> tags = id.getTags();

        sb.append(",\"tags\":{");
        if (type != null) {
            // appoptics requires at least one tag for every metric, so we hang something here that may be useful.
            sb.append("\"_type\":\"").append(type).append('"');
            if (!tags.isEmpty())
                sb.append(",");
        }

        if (!tags.isEmpty()) {
            sb.append(tags.stream()
                    .map(tag -> {
                        String key = tag.getKey();
                        if (key.equals(config.hostTag())) {
                            key = "host_hostname_alias";
                        }
                        return "\"" + config().namingConvention().tagKey(escapeJson(key)) + "\":\"" +
                                config().namingConvention().tagValue(escapeJson(tag.getValue())) + "\"";
                    })
                    .collect(joining(",")));
        }
        sb.append("}}");
        return sb.toString();
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    /**
     * A subset of the supported summary field names supported by AppOptics.
     */
    private enum Fields {
        Value("value"), Count("count"), Sum("sum"), Max("max"), Last("last");

        private final String tag;

        Fields(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    public static class Builder {
        private final AppOpticsConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(AppOpticsConfig config) {
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

        public AppOpticsMeterRegistry build() {
            return new AppOpticsMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}
