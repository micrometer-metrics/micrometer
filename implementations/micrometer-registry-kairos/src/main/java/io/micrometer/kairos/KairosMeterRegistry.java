/*
 * Copyright 2018 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.kairos;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;

/**
 * {@link MeterRegistry} for KairosDB.
 *
 * @author Anton Ilinchik
 * @author Johnny Lim
 * @since 1.1.0
 */
public class KairosMeterRegistry extends StepMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("kairos-metrics-publisher");

    private final Logger logger = LoggerFactory.getLogger(KairosMeterRegistry.class);

    private final KairosConfig config;

    private final HttpSender httpClient;

    @SuppressWarnings("deprecation")
    public KairosMeterRegistry(KairosConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private KairosMeterRegistry(KairosConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);

        config().namingConvention(new KairosNamingConvention());

        this.config = config;
        this.httpClient = httpClient;

        start(threadFactory);
    }

    public static Builder builder(KairosConfig config) {
        return new Builder(config);
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            try {
                // @formatter:off
                httpClient.post(config.uri())
                    .withBasicAuthentication(config.userName(), config.password())
                    .withJsonContent(batch.stream()
                        .flatMap(m -> m.match(
                                this::writeGauge,
                                this::writeCounter,
                                this::writeTimer,
                                this::writeSummary,
                                this::writeLongTaskTimer,
                                this::writeTimeGauge,
                                this::writeFunctionCounter,
                                this::writeFunctionTimer,
                                this::writeCustomMetric))
                        .collect(Collectors.joining(",", "[", "]")))
                    .send()
                    .onSuccess(response -> logger.debug("successfully sent {} metrics to kairos.", batch.size()))
                    .onError(response -> logger.error("failed to send metrics to kairos: {}", response.body()));
                // @formatter:on
            }
            catch (Throwable t) {
                logger.warn("failed to send metrics to kairos", t);
            }
        }
    }

    Stream<String> writeSummary(DistributionSummary summary) {
        long wallTime = config().clock().wallTime();
        return Stream.of(writeMetric(idWithSuffix(summary.getId(), "count"), wallTime, summary.count()),
                writeMetric(idWithSuffix(summary.getId(), "avg"), wallTime, summary.mean()),
                writeMetric(idWithSuffix(summary.getId(), "sum"), wallTime, summary.totalAmount()),
                writeMetric(idWithSuffix(summary.getId(), "max"), wallTime, summary.max()));
    }

    Stream<String> writeFunctionTimer(FunctionTimer timer) {
        long wallTime = config().clock().wallTime();
        return Stream.of(writeMetric(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()),
                writeMetric(idWithSuffix(timer.getId(), "avg"), wallTime, timer.mean(getBaseTimeUnit())),
                writeMetric(idWithSuffix(timer.getId(), "sum"), wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    Stream<String> writeTimer(Timer timer) {
        long wallTime = config().clock().wallTime();
        return Stream.of(writeMetric(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()),
                writeMetric(idWithSuffix(timer.getId(), "max"), wallTime, timer.max(getBaseTimeUnit())),
                writeMetric(idWithSuffix(timer.getId(), "avg"), wallTime, timer.mean(getBaseTimeUnit())),
                writeMetric(idWithSuffix(timer.getId(), "sum"), wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    // VisibleForTesting
    Stream<String> writeFunctionCounter(FunctionCounter counter) {
        double count = counter.count();
        if (Double.isFinite(count)) {
            return Stream.of(writeMetric(counter.getId(), config().clock().wallTime(), count));
        }
        return Stream.empty();
    }

    Stream<String> writeCounter(Counter counter) {
        return Stream.of(writeMetric(counter.getId(), config().clock().wallTime(), counter.count()));
    }

    // VisibleForTesting
    Stream<String> writeGauge(Gauge gauge) {
        double value = gauge.value();
        if (Double.isFinite(value)) {
            return Stream.of(writeMetric(gauge.getId(), config().clock().wallTime(), value));
        }
        return Stream.empty();
    }

    // VisibleForTesting
    Stream<String> writeTimeGauge(TimeGauge timeGauge) {
        double value = timeGauge.value(getBaseTimeUnit());
        if (Double.isFinite(value)) {
            return Stream.of(writeMetric(timeGauge.getId(), config().clock().wallTime(), value));
        }
        return Stream.empty();
    }

    Stream<String> writeLongTaskTimer(LongTaskTimer timer) {
        long wallTime = config().clock().wallTime();
        return Stream.of(writeMetric(idWithSuffix(timer.getId(), "activeTasks"), wallTime, timer.activeTasks()),
                writeMetric(idWithSuffix(timer.getId(), "duration"), wallTime, timer.duration(getBaseTimeUnit())));
    }

    // VisibleForTesting
    Stream<String> writeCustomMetric(Meter meter) {
        long wallTime = config().clock().wallTime();
        List<Tag> tags = getConventionTags(meter.getId());
        List<String> metrics = new ArrayList<>();
        for (Measurement measurement : meter.measure()) {
            double value = measurement.getValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            metrics.add(new KairosMetricBuilder().field("name", measurement.getStatistic().getTagValueRepresentation())
                .datapoints(wallTime, value)
                .tags(tags)
                .build());
        }
        return metrics.stream();
    }

    String writeMetric(Meter.Id id, long wallTime, double value) {
        return new KairosMetricBuilder().field("name", getConventionName(id))
            .datapoints(wallTime, value)
            .tags(getConventionTags(id))
            .build();
    }

    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    private static class KairosMetricBuilder {

        private final StringBuilder sb = new StringBuilder("{");

        KairosMetricBuilder field(String key, String value) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('\"').append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append('\"');
            return this;
        }

        KairosMetricBuilder datapoints(long wallTime, double value) {
            sb.append(",\"datapoints\":[[")
                .append(wallTime)
                .append(',')
                .append(DoubleFormat.wholeOrDecimal(value))
                .append("]]");
            return this;
        }

        KairosMetricBuilder tags(List<Tag> tags) {
            KairosMetricBuilder tagBuilder = new KairosMetricBuilder();
            if (tags.isEmpty()) {
                // tags field is required for KairosDB, use hostname as a default tag
                try {
                    tagBuilder.field("hostname", InetAddress.getLocalHost().getHostName());
                }
                catch (UnknownHostException ignore) {
                    /* ignore */
                }
            }
            else {
                for (Tag tag : tags) {
                    tagBuilder.field(tag.getKey(), tag.getValue());
                }
            }

            sb.append(",\"tags\":").append(tagBuilder.build());
            return this;
        }

        String build() {
            return sb.append('}').toString();
        }

    }

    public static class Builder {

        private final KairosConfig config;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(KairosConfig config) {
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

        public KairosMeterRegistry build() {
            return new KairosMeterRegistry(config, clock, threadFactory, httpClient);
        }

    }

}
