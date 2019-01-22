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
package io.micrometer.humio;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
import static java.util.stream.Collectors.joining;

/**
 * {@link MeterRegistry} for Humio.
 *
 * @author Martin Westergaard Lassen
 * @author Jon Schneider
 * @author Johnny Lim
 * @since 1.1.0
 */
public class HumioMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("humio-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(HumioMeterRegistry.class);

    private final HumioConfig config;
    private final HttpSender httpClient;

    @SuppressWarnings("deprecation")
    public HumioMeterRegistry(HumioConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private HumioMeterRegistry(HumioConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);

        config().namingConvention(new HumioNamingConvention());

        this.config = config;
        this.httpClient = httpClient;

        start(threadFactory);
    }

    private static Attribute event(String name, double value) {
        return new Attribute(name, value);
    }

    public static Builder builder(HumioConfig config) {
        return new Builder(config);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to humio every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        for (List<Meter> meters : MeterPartition.partition(this, config.batchSize())) {
            try {
                HttpSender.Request.Builder post = httpClient.post(config.uri() + "/api/v1/dataspaces/" + config.repository() + "/ingest");
                String token = config.apiToken();
                if (token != null) {
                    post.withHeader("Authorization", "Bearer " + token);
                }

                Batch batch = new Batch(config().clock().wallTime());

                String tags = "";
                Map<String, String> datasourceTags = config.tags();
                if (datasourceTags != null && !datasourceTags.isEmpty()) {
                    tags = datasourceTags.entrySet().stream().map(tag -> "\"" + tag.getKey() + "\": \"" + tag.getValue() + "\"")
                            .collect(joining(",", "\"tags\":{",  "},"));
                }

                post.withJsonContent(meters.stream()
                        .map(m -> m.match(
                                batch::writeGauge,
                                batch::writeCounter,
                                batch::writeTimer,
                                batch::writeSummary,
                                batch::writeLongTaskTimer,
                                batch::writeTimeGauge,
                                batch::writeFunctionCounter,
                                batch::writeFunctionTimer,
                                batch::writeMeter)
                        )
                        .collect(joining(",", "[{" + tags + "\"events\": [", "]}]")))
                        .send()
                        .onSuccess(response -> logger.debug("successfully sent {} metrics to humio.", meters.size()))
                        .onError(response -> logger.error("failed to send metrics to humio: {}", response.body()));
            } catch (Throwable e) {
                logger.warn("failed to send metrics to humio", e);
            }
        }
    }

    @Override
    @NonNull
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    private static class Attribute {
        private final String name;
        private final double value;

        private Attribute(String name, double value) {
            this.name = name;
            this.value = value;
        }
    }

    public static class Builder {
        private final HumioConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(HumioConfig config) {
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

        public HumioMeterRegistry build() {
            return new HumioMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }

    // VisibleForTesting
    class Batch {
        private final String timestamp;

        // VisibleForTesting
        Batch(long wallTime) {
            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(wallTime));
        }

        // VisibleForTesting
        String writeCounter(Counter counter) {
            return writeEvent(counter, event("count", counter.count()));
        }

        // VisibleForTesting
        @Nullable
        String writeFunctionCounter(FunctionCounter counter) {
            double count = counter.count();
            if (Double.isFinite(count)) {
                return writeEvent(counter, event("count", count));
            }
            return null;
        }

        // VisibleForTesting
        @Nullable
        String writeGauge(Gauge gauge) {
            double value = gauge.value();
            if (Double.isFinite(value)) {
                return writeEvent(gauge, event("value", value));
            }
            return null;
        }

        // VisibleForTesting
        @Nullable
        String writeTimeGauge(TimeGauge gauge) {
            double value = gauge.value(getBaseTimeUnit());
            if (Double.isFinite(value)) {
                return writeEvent(gauge, event("value", value));
            }
            return null;
        }

        // VisibleForTesting
        String writeFunctionTimer(FunctionTimer timer) {
            return writeEvent(timer,
                    event("count", timer.count()),
                    event("sum", timer.totalTime(getBaseTimeUnit())),
                    event("avg", timer.mean(getBaseTimeUnit())));
        }

        // VisibleForTesting
        String writeLongTaskTimer(LongTaskTimer timer) {
            return writeEvent(timer,
                    event(config().namingConvention().tagKey("active.tasks"), timer.activeTasks()),
                    event("duration", timer.duration(getBaseTimeUnit())));
        }

        // VisibleForTesting
        String writeTimer(Timer timer) {
            HistogramSnapshot snap = timer.takeSnapshot();
            return writeEvent(timer,
                    event("count", snap.count()),
                    event("sum", snap.total(getBaseTimeUnit())),
                    event("avg", snap.mean(getBaseTimeUnit())),
                    event("max", snap.max(getBaseTimeUnit())));
        }

        // VisibleForTesting
        String writeSummary(DistributionSummary summary) {
            HistogramSnapshot snap = summary.takeSnapshot();
            return writeEvent(summary,
                    event("count", snap.count()),
                    event("sum", snap.total()),
                    event("avg", snap.mean()),
                    event("max", snap.max()));
        }

        // VisibleForTesting
        String writeMeter(Meter meter) {
            return writeEvent(meter, StreamSupport.stream(meter.measure().spliterator(), false)
                    .map(ms -> event(ms.getStatistic().getTagValueRepresentation(), ms.getValue()))
                    .toArray(Attribute[]::new));
        }

        /*
          {
            "timestamp": "2016-06-06T13:00:02+02:00",
            "attributes": {
              "name": "value1"
            }
          }
         */
        // VisibleForTesting
        String writeEvent(Meter meter, Attribute... attributes) {
            StringBuilder sb = new StringBuilder();

            String name = getConventionName(meter.getId());

            sb.append("{\"timestamp\":\"").append(timestamp).append("\",\"attributes\":{\"name\":\"")
                    .append(escapeJson(name)).append('"');

            for (Attribute attribute : attributes) {
                sb.append(",\"").append(attribute.name).append("\":").append(DoubleFormat.decimalOrWhole(attribute.value));
            }

            List<Tag> tags = getConventionTags(meter.getId());
            for (Tag tag : tags) {
                String key = tag.getKey();
                for (Attribute attribute : attributes) {
                    if (attribute.name.equals(key)) {
                        key = "_" + key;
                        break;
                    }
                }

                sb.append(",\"").append(escapeJson(key)).append("\":\"").append(escapeJson(tag.getValue())).append('"');
            }

            sb.append("}}");
            return sb.toString();
        }
    }
}
