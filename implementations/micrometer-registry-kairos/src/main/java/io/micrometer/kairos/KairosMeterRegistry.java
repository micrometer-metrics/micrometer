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
package io.micrometer.kairos;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.ipc.http.HttpClient;
import io.micrometer.core.ipc.http.HttpUrlConnectionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.micrometer.core.instrument.Meter.Type.match;

/**
 * @author Anton Ilinchik
 */
public class KairosMeterRegistry extends StepMeterRegistry {
    private final Logger logger = LoggerFactory.getLogger(KairosMeterRegistry.class);
    private final KairosConfig config;
    private final HttpClient httpClient;

    public KairosMeterRegistry(KairosConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory(), new HttpUrlConnectionClient(config.connectTimeout(), config.readTimeout()));
    }

    private KairosMeterRegistry(KairosConfig config, Clock clock, ThreadFactory threadFactory, HttpClient httpClient) {
        super(config, clock);

        config().namingConvention(new KairosNamingConvention());

        this.config = config;
        this.httpClient = httpClient;

        if (config.enabled())
            start(threadFactory);
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            try {
                httpClient.post(config.uri())
                        .withBasicAuthentication(config.userName(), config.password())
                        .withJsonContent(
                                batch.stream().flatMap(m -> match(m,
                                        this::writeGauge,
                                        this::writeCounter,
                                        this::writeTimer,
                                        this::writeSummary,
                                        this::writeLongTaskTimer,
                                        this::writeTimeGauge,
                                        this::writeFunctionCounter,
                                        this::writeFunctionTimer,
                                        this::writeCustomMetric)
                                ).collect(Collectors.joining(",", "[", "]"))
                        )
                        .print()
                        .send()
                        .onSuccess(response -> logger.debug("successfully sent {} metrics to kairos.", batch.size()))
                        .onError(response -> logger.error("failed to send metrics to kairos: {}", response.body()));
            } catch (Throwable t) {
                logger.warn("failed to send metrics to kairos", t);
            }
        }
    }

    private static class KairosMetricBuilder {
        private StringBuilder sb = new StringBuilder("{");

        KairosMetricBuilder field(String key, String value) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('\"').append(key).append("\":\"").append(value).append('\"');
            return this;
        }

        KairosMetricBuilder datapoints(Long wallTime, Number value) {
            sb.append(",\"datapoints\":[[").append(wallTime).append(',').append(value).append("]]");
            return this;
        }

        KairosMetricBuilder tags(List<Tag> tags) {
            KairosMetricBuilder tagBuilder = new KairosMetricBuilder();
            if (tags.isEmpty()) {
                // tags field is required for KairosDB, use hostname as a default tag
                try {
                    tagBuilder.field("hostname", InetAddress.getLocalHost().getHostName());
                } catch (UnknownHostException ignore) {
                    /* ignore */
                }
            } else {
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

    Stream<String> writeSummary(DistributionSummary summary) {
        long wallTime = config().clock().wallTime();
        return Stream.of(
                writeMetric(idWithSuffix(summary.getId(), "count"), wallTime, summary.count()),
                writeMetric(idWithSuffix(summary.getId(), "avg"), wallTime, summary.mean()),
                writeMetric(idWithSuffix(summary.getId(), "sum"), wallTime, summary.totalAmount()),
                writeMetric(idWithSuffix(summary.getId(), "max"), wallTime, summary.max())
        );
    }

    Stream<String> writeFunctionTimer(FunctionTimer timer) {
        long wallTime = config().clock().wallTime();
        return Stream.of(
                writeMetric(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()),
                writeMetric(idWithSuffix(timer.getId(), "avg"), wallTime, timer.mean(getBaseTimeUnit())),
                writeMetric(idWithSuffix(timer.getId(), "sum"), wallTime, timer.totalTime(getBaseTimeUnit()))
        );
    }

    Stream<String> writeTimer(Timer timer) {
        long wallTime = config().clock().wallTime();
        return Stream.of(
                writeMetric(idWithSuffix(timer.getId(), "count"), wallTime, timer.count()),
                writeMetric(idWithSuffix(timer.getId(), "max"), wallTime, timer.max(getBaseTimeUnit())),
                writeMetric(idWithSuffix(timer.getId(), "avg"), wallTime, timer.mean(getBaseTimeUnit())),
                writeMetric(idWithSuffix(timer.getId(), "sum"), wallTime, timer.totalTime(getBaseTimeUnit()))
        );
    }

    Stream<String> writeFunctionCounter(FunctionCounter counter) {
        return Stream.of(writeMetric(counter.getId(), config().clock().wallTime(), counter.count()));
    }

    Stream<String> writeCounter(Counter counter) {
        return Stream.of(writeMetric(counter.getId(), config().clock().wallTime(), counter.count()));
    }

    Stream<String> writeGauge(Gauge gauge) {
        Double value = gauge.value();
        return value.isNaN() ? Stream.empty() : Stream.of(writeMetric(gauge.getId(), config().clock().wallTime(), value));
    }

    Stream<String> writeTimeGauge(TimeGauge timeGauge) {
        Double value = timeGauge.value(getBaseTimeUnit());
        return value.isNaN() ? Stream.empty() : Stream.of(writeMetric(timeGauge.getId(), config().clock().wallTime(), value));
    }

    Stream<String> writeLongTaskTimer(LongTaskTimer timer) {
        long wallTime = config().clock().wallTime();
        return Stream.of(
                writeMetric(idWithSuffix(timer.getId(), "activeTasks"), wallTime, timer.activeTasks()),
                writeMetric(idWithSuffix(timer.getId(), "duration"), wallTime, timer.duration(getBaseTimeUnit()))
        );
    }

    private Stream<String> writeCustomMetric(final Meter meter) {
        long wallTime = config().clock().wallTime();
        return StreamSupport.stream(meter.measure().spliterator(), false)
                .map(ms -> new KairosMetricBuilder()
                        .field("name", ms.getStatistic().getTagValueRepresentation())
                        .datapoints(wallTime, ms.getValue())
                        .tags(getConventionTags(meter.getId()))
                        .build());
    }

    String writeMetric(Meter.Id id, long wallTime, Number value) {
        return new KairosMetricBuilder()
                .field("name", getConventionName(id))
                .datapoints(wallTime, value)
                .tags(getConventionTags(id))
                .build();
    }

    private Meter.Id idWithSuffix(final Meter.Id id, final String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static Builder builder(KairosConfig config) {
        return new Builder(config);
    }

    public static class Builder {
        private final KairosConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = Executors.defaultThreadFactory();
        private HttpClient httpClient;

        public Builder(KairosConfig config) {
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

        public KairosMeterRegistry build() {
            return new KairosMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}
