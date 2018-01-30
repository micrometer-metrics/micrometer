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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.joining;

/**
 * @author Jon Schneider
 */
@Incubating(since = "1.0.0-rc.5")
public class NewRelicMeterRegistry extends StepMeterRegistry {
    private final NewRelicConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(NewRelicMeterRegistry.class);

    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;
        config().namingConvention(NamingConvention.camelCase);
        start(threadFactory);
    }

    @Override
    protected void publish() {
        try {
            URL insightsEndpoint = URI.create(config.uri() + "/v1/accounts/" + config.accountId() + "/events").toURL();

            // New Relic's Insights API limits us to 1000 events per call
            final int batchSize = Math.min(config.batchSize(), 1000);

            List<Event> events = new ArrayList<>();

            for (Meter meter : getMeters()) {
                Meter.Id id = meter.getId();

                if (meter instanceof Timer) {
                    HistogramSnapshot t = ((Timer) meter).takeSnapshot(false);

                    events.add(event(id, "count", t.count()));
                    events.add(event(id, "sum", t.total(getBaseTimeUnit())));
                    events.add(event(id, "avg", t.mean(getBaseTimeUnit())));
                    events.add(event(id, "max", t.max(getBaseTimeUnit())));

                    for (ValueAtPercentile valueAtPercentile : t.percentileValues()) {
                        events.add(event(id, "percentile", valueAtPercentile.value(getBaseTimeUnit()), "phi",
                            DoubleFormat.toString(valueAtPercentile.percentile())));
                    }
                } else if (meter instanceof FunctionTimer) {
                    FunctionTimer t = (FunctionTimer) meter;
                    events.add(event(id, "count", t.count()));
                    events.add(event(id, "sum", t.count()));
                    events.add(event(id, "mean", t.mean(getBaseTimeUnit())));
                } else if (meter instanceof DistributionSummary) {
                    HistogramSnapshot t = ((DistributionSummary) meter).takeSnapshot(false);

                    events.add(event(id, "count", t.count()));
                    events.add(event(id, "sum", t.total()));
                    events.add(event(id, "avg", t.mean()));
                    events.add(event(id, "max", t.max()));

                    for (ValueAtPercentile valueAtPercentile : t.percentileValues()) {
                        events.add(event(id, "percentile", valueAtPercentile.value(), "phi",
                            DoubleFormat.toString(valueAtPercentile.percentile())));
                    }
                } else {
                    for (Measurement measurement : meter.measure()) {
                        events.add(event(id, measurement.getStatistic().toString(), measurement.getValue()));
                    }
                }

                if (events.size() > batchSize) {
                    sendEvents(insightsEndpoint, events.subList(0, batchSize));
                    events = new ArrayList<>(events.subList(batchSize, events.size()));
                } else if (events.size() == batchSize) {
                    sendEvents(insightsEndpoint, events);
                    events = new ArrayList<>();
                }
            }

            // drain the remaining event list
            if (!events.isEmpty()) {
                sendEvents(insightsEndpoint, events);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed New Relic insights endpoint, see '" + config.prefix() + ".uri'", e);
        } catch (Throwable t) {
            logger.warn("failed to send metrics", t);
        }
    }

    private Event event(Meter.Id id, String statistic, Number value, String... additionalTags) {
        Event event = new Event();

        event.put("eventType", getConventionName(id));
        event.put("statistic", statistic);
        event.put("value", value);

        for (int i = 0; i < additionalTags.length; i += 2) {
            event.put(additionalTags[i], additionalTags[i + 1]);
        }

        id.getTags().forEach(t -> event.put(t.getKey(), t.getValue()));

        return event;
    }

    // TODO HTTP/1.1 Persistent connections are supported
    private void sendEvents(URL insightsEndpoint, List<Event> events) {

        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) insightsEndpoint.openConnection();
            con.setConnectTimeout((int) config.connectTimeout().toMillis());
            con.setReadTimeout((int) config.readTimeout().toMillis());
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("X-Insert-Key", config.apiKey());

            con.setDoOutput(true);

            String body = mapper.writeValueAsString(events);

            try (OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            int status = con.getResponseCode();

            if (status >= 200 && status < 300) {
                logger.info("successfully sent {} events to New Relic", events.size());
            } else if (status >= 400) {
                try (InputStream in = con.getErrorStream()) {
                    logger.error("failed to send metrics: " + new BufferedReader(new InputStreamReader(in))
                        .lines().collect(joining("\n")));
                }
            } else {
                logger.error("failed to send metrics: http " + status);
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

    private class Event extends HashMap<String, Object> {
    }
}
