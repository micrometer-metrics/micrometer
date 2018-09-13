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
import io.micrometer.core.instrument.util.*;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Publishes metrics to New Relic Insights.
 *
 * @author Jon Schneider
 */
public class NewRelicMeterRegistry extends StepMeterRegistry {
    private final NewRelicConfig config;
    private final Logger logger = LoggerFactory.getLogger(NewRelicMeterRegistry.class);

    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;

        requireNonNull(config.accountId());
        requireNonNull(config.apiKey());

        config().namingConvention(new NewRelicNamingConvention());
        start(threadFactory);
    }

    @Override
    protected void publish() {
        try {
            URL insightsEndpoint = URI.create(config.uri() + "/v1/accounts/" + config.accountId() + "/events").toURL();

            // New Relic's Insights API limits us to 1000 events per call
            final int batchSize = Math.min(config.batchSize(), 1000);

            List<String> events = getMeters().stream().flatMap(meter -> Meter.Type.match(meter,
                    this::writeGauge,
                    this::writeCounter,
                    this::writeTimer,
                    this::writeSummary,
                    this::writeLongTaskTimer,
                    this::writeTimeGauge,
                    this::writeFunctionCounter,
                    this::writeFunctionTimer,
                    this::writeMeter)).collect(toList());

            if (events.size() > batchSize) {
                sendEvents(insightsEndpoint, events.subList(0, batchSize));
                events = new ArrayList<>(events.subList(batchSize, events.size()));
            } else if (events.size() == batchSize) {
                sendEvents(insightsEndpoint, events);
                events = new ArrayList<>();
            }

            // drain the remaining event list
            if (!events.isEmpty()) {
                sendEvents(insightsEndpoint, events);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("malformed New Relic insights endpoint -- see the 'uri' configuration", e);
        } catch (Throwable t) {
            logger.warn("failed to send metrics", t);
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

        return "{\"eventType\":\"" + getConventionName(id) + "\"" +
                Arrays.stream(attributes).map(attr -> ",\"" + attr.getName() + "\":" + DoubleFormat.decimalOrWhole(attr.getValue().doubleValue()))
                        .collect(Collectors.joining("")) + tagsJson.toString() + "}";
    }

    private void sendEvents(URL insightsEndpoint, List<String> events) {

        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) insightsEndpoint.openConnection();
            con.setConnectTimeout((int) config.connectTimeout().toMillis());
            con.setReadTimeout((int) config.readTimeout().toMillis());
            con.setRequestMethod(HttpMethod.POST);
            con.setRequestProperty(HttpHeader.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            con.setRequestProperty("X-Insert-Key", config.apiKey());

            con.setDoOutput(true);

            String body = "[" + events.stream().collect(Collectors.joining(",")) + "]";

            logger.trace("Sending payload to New Relic:");
            logger.trace(body);

            try (OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            int status = con.getResponseCode();

            if (status >= 200 && status < 300) {
                logger.info("successfully sent {} events to New Relic", events.size());
            } else if (status >= 400) {
                if (logger.isErrorEnabled()) {
                    logger.error("failed to send metrics: {}", IOUtils.toString(con.getErrorStream()));
                }
            } else {
                logger.error("failed to send metrics: http {}", status);
            }

        } catch (Throwable e) {
            logger.warn("failed to send metrics", e);
        } finally {
            quietlyCloseUrlConnection(con);
        }
    }

    private void quietlyCloseUrlConnection(@Nullable HttpURLConnection con) {
        try {
            if (con != null) {
                con.disconnect();
            }
        } catch (Exception ignore) {
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
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
