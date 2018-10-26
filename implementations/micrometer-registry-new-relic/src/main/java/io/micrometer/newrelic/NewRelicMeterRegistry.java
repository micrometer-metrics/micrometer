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
package io.micrometer.newrelic;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpClient;
import io.micrometer.core.ipc.http.HttpUrlConnectionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

/**
 * Publishes metrics to New Relic Insights.
 *
 * @author Jon Schneider
 */
public class NewRelicMeterRegistry extends StepMeterRegistry {
    private final NewRelicConfig config;
    private final HttpClient httpClient;
    private final Logger logger = LoggerFactory.getLogger(NewRelicMeterRegistry.class);

    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    /**
     * @deprecated Use {@link #builder(NewRelicConfig)} instead.
     */
    @Deprecated
    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionClient(config.connectTimeout(), config.readTimeout()));
    }

    private NewRelicMeterRegistry(NewRelicConfig config, Clock clock, ThreadFactory threadFactory, HttpClient httpClient) {
        super(config, clock);
        this.config = config;
        this.httpClient = httpClient;

        requireNonNull(config.accountId());
        requireNonNull(config.apiKey());

        config().namingConvention(new NewRelicNamingConvention());
        start(threadFactory);
    }

    public static Builder builder(NewRelicConfig config) {
        return new Builder(config);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("Publishing metrics to new relic every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        String insightsEndpoint = config.uri() + "/v1/accounts/" + config.accountId() + "/events";

        // New Relic's Insights API limits us to 1000 events per call
        for (List<Meter> batch : MeterPartition.partition(this, Math.min(config.batchSize(), 1000))) {
            sendEvents(insightsEndpoint, batch.stream().flatMap(meter -> meter.apply(
                    this::writeGauge,
                    this::writeCounter,
                    this::writeTimer,
                    this::writeSummary,
                    this::writeLongTaskTimer,
                    this::writeTimeGauge,
                    this::writeFunctionCounter,
                    this::writeFunctionTimer,
                    this::writeMeter)));
        }
    }

    private Stream<String> writeLongTaskTimer(LongTaskTimer ltt) {
        return Stream.of(
                event(ltt.getId(),
                        new Attribute("activeTasks", ltt.activeTasks()),
                        new Attribute("duration", ltt.duration(getBaseTimeUnit())))
        );
    }

    private Stream<String> writeFunctionCounter(FunctionCounter counter) {
        return Stream.of(event(counter.getId(), new Attribute("throughput", counter.count())));
    }

    private Stream<String> writeCounter(Counter counter) {
        return Stream.of(event(counter.getId(), new Attribute("throughput", counter.count())));
    }

    private Stream<String> writeGauge(Gauge gauge) {
        Double value = gauge.value();
        return value.isNaN() ? Stream.empty() : Stream.of(event(gauge.getId(), new Attribute("value", value)));
    }

    private Stream<String> writeTimeGauge(TimeGauge gauge) {
        Double value = gauge.value(getBaseTimeUnit());
        return value.isNaN() ? Stream.empty() : Stream.of(event(gauge.getId(), new Attribute("value", value)));
    }

    private Stream<String> writeSummary(DistributionSummary summary) {
        return Stream.of(
                event(summary.getId(),
                        new Attribute("count", summary.count()),
                        new Attribute("avg", summary.mean()),
                        new Attribute("total", summary.totalAmount()),
                        new Attribute("max", summary.max())
                )
        );
    }

    private Stream<String> writeTimer(Timer timer) {
        return Stream.of(event(timer.getId(),
                new Attribute("count", timer.count()),
                new Attribute("avg", timer.mean(getBaseTimeUnit())),
                new Attribute("totalTime", timer.totalTime(getBaseTimeUnit())),
                new Attribute("max", timer.max(getBaseTimeUnit()))
        ));
    }

    private Stream<String> writeFunctionTimer(FunctionTimer timer) {
        return Stream.of(
                event(timer.getId(),
                        new Attribute("count", timer.count()),
                        new Attribute("avg", timer.mean(getBaseTimeUnit())),
                        new Attribute("totalTime", timer.totalTime(getBaseTimeUnit()))
                )
        );
    }

    private Stream<String> writeMeter(Meter meter) {
        return Stream.of(
                event(meter.getId(),
                        stream(meter.measure().spliterator(), false)
                                .map(measure -> new Attribute(measure.getStatistic().getTagValueRepresentation(), measure.getValue()))
                                .toArray(Attribute[]::new)
                )
        );
    }

    private String event(Meter.Id id, Attribute... attributes) {
        return event(id, Tags.empty(), attributes);
    }

    private String event(Meter.Id id, Iterable<Tag> extraTags, Attribute... attributes) {
        StringBuilder tagsJson = new StringBuilder();

        for (Tag tag : getConventionTags(id)) {
            tagsJson.append(",\"").append(tag.getKey()).append("\":\"").append(tag.getValue()).append("\"");
        }

        NamingConvention convention = config().namingConvention();
        for (Tag tag : extraTags) {
            tagsJson.append(",\"").append(convention.tagKey(tag.getKey())).append("\":\"").append(convention.tagValue(tag.getValue())).append("\"");
        }

        return Arrays.stream(attributes)
                .map(attr -> ",\"" + attr.getName() + "\":" + DoubleFormat.decimalOrWhole(attr.getValue().doubleValue()))
                .collect(Collectors.joining("", "{\"eventType\":\"" + getConventionName(id) + "\"", tagsJson + "}"));
    }

    private void sendEvents(String insightsEndpoint, Stream<String> events) {
        try {
            AtomicInteger totalEvents = new AtomicInteger();

            httpClient.post(insightsEndpoint)
                    .withHeader("X-Insert-Key", config.apiKey())
                    .withJsonContent(events.peek(ev -> totalEvents.incrementAndGet()).collect(Collectors.joining(",", "[", "]")))
                    .send()
                    .onSuccess(response -> logger.debug("successfully sent {} metrics to New Relic.", totalEvents))
                    .onError(response -> logger.error("failed to send metrics to new relic: {}", response.body()));
        } catch (Throwable e) {
            logger.warn("failed to send metrics to new relic", e);
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    public static class Builder {
        private final NewRelicConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = Executors.defaultThreadFactory();
        private HttpClient httpClient;

        public Builder(NewRelicConfig config) {
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

        public NewRelicMeterRegistry build() {
            return new NewRelicMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }

    private class Attribute {
        private final String name;
        private final Number value;

        private Attribute(String name, Number value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Number getValue() {
            return value;
        }
    }
}
