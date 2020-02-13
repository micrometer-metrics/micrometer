/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.newrelic;

import com.newrelic.api.agent.Insights;
import com.newrelic.api.agent.NewRelic;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.InvalidConfigurationException;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
import static io.micrometer.newrelic.NewRelicIntegration.API;
import static io.micrometer.newrelic.NewRelicIntegration.APM;
import static java.util.Objects.requireNonNull;

/**
 * Publishes metrics to New Relic Insights.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Galen Schmidt
 * @since 1.0.0
 */
public class NewRelicMeterRegistry extends StepMeterRegistry {

    private static final Logger logger = LoggerFactory.getLogger(NewRelicMeterRegistry.class);

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("new-relic-metrics-publisher");

    private final NewRelicConfig config;

    private final HttpSender httpClient;

    private final Insights insights;

    private final Runnable publisher;

    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clock  The clock to use for timings.
     */
    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY);
    }

    /**
     * @param config        Configuration options for the registry that are describable as properties.
     * @param clock         The clock to use for timings.
     * @param threadFactory The thread factory to use to create the publishing thread.
     * @deprecated Use {@link #builder(NewRelicConfig)} instead.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()), NewRelic.getAgent().getInsights());
    }

    private NewRelicMeterRegistry(NewRelicConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient, Insights insights) {
        super(requireNonNull(config, "config"), requireNonNull(clock, "clock"));

        this.config = config;
        this.httpClient = httpClient;
        this.insights = insights;

        NewRelicIntegration integration = requireNonEmpty("integration", config.integration());

        if (API.equals(integration)) {
            requireNonEmpty("accountId", config.accountId());
            requireNonEmpty("apiKey", config.apiKey());
            requireNonEmpty("uri", config.uri());
            requireNonNull(httpClient, "httpClient");
            publisher = this::publishViaApi;
        } else if (APM.equals(integration)) {
            requireNonNull(insights, "insights");
            publisher = this::publishViaApm;
        } else {
            // the only way it's possible to get here is if a value is added to the enum,
            // without adding a branch to the if/else tree
            throw new InvalidConfigurationException("Unsupported New Relic integration " + integration);
        }

        if (!config.meterNameEventTypeEnabled()) {
            requireNonEmpty("eventType", config.eventType());
        }

        config().namingConvention(new NewRelicNamingConvention());
        start(requireNonNull(threadFactory, "threadFactory"));
    }

    public static Builder builder(NewRelicConfig config) {
        return new Builder(config);
    }

    @Override
    public void start(ThreadFactory threadFactory) {

        if (config.enabled()) {
            logger.info("publishing metrics to new relic every " + TimeUtils.format(config.step()));
        } else {
            logger.debug("publishing metrics to new relic is disabled");
        }

        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        publisher.run();
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    // VisibleForTesting
    @Nullable
    NewRelicEvent eventFor(Meter meter) {
        return meter.match(
                this::toEvent, // Gauge
                this::toEvent, // Counter
                this::toEvent, // Timer
                this::toEvent, // DistributionSummary
                this::toEvent, // LongTaskTimer
                this::toEvent, // TimeGauge
                this::toEvent, // FunctionCounter
                this::toEvent, // FunctionTimer
                this::toEvent  // Meter
        );
    }

    @Nullable
    private NewRelicEvent toEvent(Gauge gauge) {
        double value = gauge.value();

        if (!Double.isFinite(value)) {
            return null;
        }

        return newEvent(gauge.getId(),
                "value", value
        );
    }

    private NewRelicEvent toEvent(Counter counter) {
        return newEvent(counter.getId(),
                "throughput", counter.count()
        );
    }

    private NewRelicEvent toEvent(Timer timer) {
        return newEvent(timer.getId(),
                "count", timer.count(),
                "avg", timer.mean(getBaseTimeUnit()),
                "totalTime", timer.totalTime(getBaseTimeUnit()),
                "max", timer.max(getBaseTimeUnit()),
                "timeUnit", getBaseTimeUnit().name().toLowerCase()
        );
    }

    private NewRelicEvent toEvent(DistributionSummary summary) {
        return newEvent(summary.getId(),
                "count", summary.count(),
                "avg", summary.mean(),
                "total", summary.totalAmount(),
                "max", summary.max()
        );
    }

    private NewRelicEvent toEvent(LongTaskTimer ltt) {
        return newEvent(ltt.getId(),
                "activeTasks", ltt.activeTasks(),
                "duration", ltt.duration(getBaseTimeUnit()),
                "timeUnit", getBaseTimeUnit().name().toLowerCase()
        );
    }

    @Nullable
    private NewRelicEvent toEvent(TimeGauge gauge) {
        double value = gauge.value(getBaseTimeUnit());

        if (!Double.isFinite(value)) {
            return null;
        }

        return newEvent(gauge.getId(),
                "value", value,
                "timeUnit", getBaseTimeUnit().name().toLowerCase()
        );
    }

    @Nullable
    private NewRelicEvent toEvent(FunctionCounter counter) {
        double count = counter.count();

        if (!Double.isFinite(count)) {
            return null;
        }

        return newEvent(counter.getId(),
                "throughput", count
        );
    }

    private NewRelicEvent toEvent(FunctionTimer timer) {
        return newEvent(timer.getId(),
                "count", timer.count(),
                "avg", timer.mean(getBaseTimeUnit()),
                "totalTime", timer.totalTime(getBaseTimeUnit()),
                "timeUnit", getBaseTimeUnit().name().toLowerCase()
        );
    }

    @Nullable
    private NewRelicEvent toEvent(Meter meter) {
        Map<String, Double> attributes = new HashMap<>();

        // Snapshot values should be used throughout this method as there are chances for values to be changed in-between.
        for (Measurement measurement : meter.measure()) {
            double value = measurement.getValue();

            if (!Double.isFinite(value)) {
                continue;
            }

            attributes.put(measurement.getStatistic().getTagValueRepresentation(), value);
        }

        if (attributes.isEmpty()) {
            return null;
        }

        // array representation of the attributes.
        // Entries with odd indices are map keys, and entries with even indices are map values.
        Object[] attributeArray = attributes.entrySet().stream()
                .flatMap(entry -> Stream.of(entry.getKey(), entry.getValue()))
                .toArray(Object[]::new);

        return newEvent(meter.getId(), attributeArray);
    }

    private NewRelicEvent newEvent(Meter.Id id, Object... attributeArray) {
        Map<String, Object> attributes = new HashMap<>();

        // only inject metadata if we're not using the metric name as the event type
        if (!config.meterNameEventTypeEnabled()) {
            attributes.put("metricName", id.getConventionName(config().namingConvention()));
            attributes.put("metricType", String.valueOf(id.getType()));
        }

        for (Tag tag : getConventionTags(id)) {
            attributes.put(tag.getKey(), tag.getValue());
        }

        NamingConvention convention = config().namingConvention();

        for (int i = 0; i < attributeArray.length; i += 2) {
            String key = convention.tagKey(String.valueOf(attributeArray[i]));
            Object value = attributeArray[i + 1];

            // ensure the attribute value is either a Number or a String
            if (!(value instanceof Number)) {
                value = convention.tagValue(String.valueOf(value));
            }

            attributes.put(key, value);
        }

        return new NewRelicEvent(getEventType(id), attributes);
    }

    private String getEventType(Meter.Id id) {
        if (config.meterNameEventTypeEnabled()) {
            return id.getConventionName(config().namingConvention());
        } else {
            return config.eventType();
        }
    }

    private Stream<Stream<NewRelicEvent>> batchMetrics() {
        // New Relic's Insights API limits us to 1000 events per call
        // 1:1 mapping between Micrometer meters and New Relic events
        return MeterPartition.partition(this, Math.min(config.batchSize(), 1000)).stream()
                .map(meters -> meters.stream().map(this::eventFor).filter(Objects::nonNull));
    }

    private void publishViaApm() {
        batchMetrics().forEach(batch -> {
            try {
                AtomicInteger count = new AtomicInteger();

                batch.peek(e -> count.incrementAndGet()).forEach(event -> insights.recordCustomEvent(event.getEventType(), event.getAttributes()));

                logger.debug("successfully sent {} metrics to New Relic.", count);

            } catch (Throwable e) {
                logger.warn("failed to send metrics to New Relic", e);
            }
        });
    }

    private void publishViaApi() {
        String insightsEndpoint = config.uri() + "/v1/accounts/" + config.accountId() + "/events";

        batchMetrics().forEach(batch -> {
            try {

                AtomicInteger count = new AtomicInteger();

                String json = batch.peek(e -> count.incrementAndGet())
                        .map(NewRelicEvent::toJson)
                        .collect(Collectors.joining(",", "[", "]"));

                httpClient.post(insightsEndpoint)
                        .withHeader("X-Insert-Key", config.apiKey())
                        .withJsonContent(json)
                        .send()
                        .onSuccess(response -> logger.debug("successfully sent {} metrics to New Relic.", count))
                        .onError(response -> logger.error("failed to send metrics to new relic: http {} {}", response.code(), response.body()));

            } catch (Throwable e) {
                logger.warn("failed to send metrics to New Relic", e);
            }

        });
    }

    private <T> T requireNonEmpty(String name, @Nullable T value) {

        if (StringUtils.isEmpty(Objects.toString(value, null))) {
            throw new MissingRequiredConfigurationException(name + " must be set to report metrics to New Relic");
        }

        return value;
    }

    public static class Builder {

        private final NewRelicConfig config;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        private HttpSender httpClient;

        private Insights insights = NewRelic.getAgent().getInsights();

        @SuppressWarnings("deprecation")
        Builder(NewRelicConfig config) {
            this.config = requireNonNull(config, "config");
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

        public Builder insights(Insights insights) {
            this.insights = insights;
            return this;
        }

        public NewRelicMeterRegistry build() {
            return new NewRelicMeterRegistry(config, clock, threadFactory, httpClient, insights);
        }
    }

    /**
     * POJO representation of an Insights event.
     */
    // VisibleForTesting
    static class NewRelicEvent {

        private final String eventType;

        private final Map<String, Object> attributes;

        /**
         * Create a new {@link NewRelicEvent}.
         * All values provided should <b>NOT</b> be JSON escaped, {@link #toJson()} will perform JSON escaping.
         *
         * @param eventType  The event type
         * @param attributes Attributes for the event
         */
        private NewRelicEvent(String eventType, Map<String, Object> attributes) {
            this.eventType = eventType;
            this.attributes = attributes;
        }

        public String getEventType() {
            return this.eventType;
        }

        public Map<String, Object> getAttributes() {
            return this.attributes;
        }

        /**
         * Convert this event into a JSON payload suitable for use with the NewRelic Insights API
         *
         * @return The JSON representation of this event
         */
        public String toJson() {
            final StringBuilder json = new StringBuilder();

            json.append("{\"eventType\":\"");
            json.append(escapeJson(getEventType()));
            json.append("\"");

            getAttributes().forEach((key, value) -> {

                json.append(",\"");
                json.append(escapeJson(key));
                json.append("\":");

                // the only supported attribute values are Numbers and Strings
                if (value instanceof Number) {
                    json.append(DoubleFormat.wholeOrDecimal(((Number) value).doubleValue()));
                } else {
                    json.append("\"");
                    json.append(escapeJson(String.valueOf(value)));
                    json.append("\"");
                }
            });

            json.append("}");

            return json.toString();
        }
    }

}
