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
package io.micrometer.signalfx;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.joining;

/**q
 * @author Jon Schneider
 */
public class SignalFxMeterRegistry extends StepMeterRegistry {
    private final Logger logger = LoggerFactory.getLogger(SignalFxMeterRegistry.class);
    private final SignalFxConfig config;

    private final URL postTimeSeriesEndpoint;

    public SignalFxMeterRegistry(SignalFxConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public SignalFxMeterRegistry(SignalFxConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;

        try {
            this.postTimeSeriesEndpoint = URI.create(config.uri() + "/datapoint").toURL();
        } catch (MalformedURLException e) {
            // not possible
            throw new RuntimeException(e);
        }

        config().namingConvention(new SignalFxNamingConvention());

        start(threadFactory);
    }

    @Override
    protected void publish() {
        try {
            HttpURLConnection con = null;

            final long timestamp = clock.wallTime();

            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                try {
                    con = (HttpURLConnection) postTimeSeriesEndpoint.openConnection();
                    con.setConnectTimeout((int) config.connectTimeout().toMillis());
                    con.setReadTimeout((int) config.readTimeout().toMillis());
                    con.setRequestMethod("POST");
                    con.setRequestProperty("Content-Type", "application/json");
                    con.setRequestProperty("X-SF-TOKEN", config.accessToken());
                    con.setDoOutput(true);

                    /*
                    Example post body from SignalFX API docs (https://developers.signalfx.com/reference#datapoint).
                    Timestamp is optional, but should be sent for greatest accuracy.
                    "{
                        "gauge": [{
                          "metric": "test.gauge",
                          "value": 42,
                          "dimensions": { "host": "testserver" }
                        },
                        {
                          "metric": "test.gauge.with_timestamp",
                          "value": 42,
                          "timestamp": 1485801354682,
                          "dimensions": { "host": "testserver" }
                        }],

                        "counter": [{
                          "metric": "test.counter",
                          "value": 1,
                          "dimensions": { "host": "testserver" }
                        }],

                        "cumulative_counter": [{
                          "metric": "test.cumulative_counter",
                          "value": 100,
                          "dimensions": { "host": "testserver" }
                        }]
                    }"
                    */

                    SignalFxPayload payload = new SignalFxPayload();

                    for (Meter meter : batch) {
                        if (meter instanceof Counter) {
                            addCounter((Counter) meter, payload, timestamp);
                        } else if (meter instanceof Timer) {
                            addTimer((Timer) meter, payload, timestamp);
                        } else if (meter instanceof DistributionSummary) {
                            addDistributionSummary((DistributionSummary) meter, payload, timestamp);
                        } else if (meter instanceof TimeGauge) {
                            addTimeGauge((TimeGauge) meter, payload, timestamp);
                        } else if (meter instanceof Gauge) {
                            addGauge((Gauge) meter, payload, timestamp);
                        } else if (meter instanceof FunctionTimer) {
                            addFunctionTimer((FunctionTimer) meter, payload, timestamp);
                        } else if (meter instanceof FunctionCounter) {
                            addFunctionCounter((FunctionCounter) meter, payload, timestamp);
                        } else if (meter instanceof LongTaskTimer) {
                            addLongTaskTimer((LongTaskTimer) meter, payload, timestamp);
                        } else {
                            addMeter(meter, payload, timestamp);
                        }
                    }

                    try (OutputStream os = con.getOutputStream()) {
                        os.write(payload.toJson().getBytes());
                        os.flush();
                    }

                    int status = con.getResponseCode();

                    if (status >= 200 && status < 300) {
                        logger.info("successfully sent " + batch.size() + " metrics to SignalFX");
                    } else if (status >= 400) {
                        try (InputStream in = con.getErrorStream()) {
                            logger.error("failed to send metrics: " + new BufferedReader(new InputStreamReader(in))
                                .lines().collect(joining("\n")));
                        }
                    } else {
                        logger.error("failed to send metrics: http " + status);
                    }
                } finally {
                    quietlyCloseUrlConnection(con);
                }
            }
        } catch (Exception e) {
            logger.warn("failed to send metrics", e);
        }
    }

    private void addMeter(Meter meter, SignalFxPayload payload, long timestamp) {
        for (Measurement measurement : meter.measure()) {
            SignalFxTimeseries ts = new SignalFxTimeseries(meter, NamingConvention.camelCase.tagKey(measurement.getStatistic().toString()),
                measurement.getValue(), timestamp);

            switch (measurement.getStatistic()) {
                case Total:
                case TotalTime:
                case Count:
                case Duration:
                    payload.counters.add(ts);
                    break;
                case Max:
                case Value:
                case Unknown:
                case ActiveTasks:
                    payload.gauges.add(ts);
                    break;
            }
        }
    }

    private void addLongTaskTimer(LongTaskTimer longTaskTimer, SignalFxPayload payload, long timestamp) {
        payload.gauges.add(new SignalFxTimeseries(longTaskTimer, "activeTasks", longTaskTimer.activeTasks(), timestamp));
        payload.counters.add(new SignalFxTimeseries(longTaskTimer, "duration", longTaskTimer.duration(getBaseTimeUnit()), timestamp));
    }

    private void addTimeGauge(TimeGauge timeGauge, SignalFxPayload payload, long timestamp) {
        payload.gauges.add(new SignalFxTimeseries(timeGauge, timeGauge.value(getBaseTimeUnit()), timestamp));
    }

    private void addGauge(Gauge gauge, SignalFxPayload payload, long timestamp) {
        payload.gauges.add(new SignalFxTimeseries(gauge, gauge.value(), timestamp));
    }

    private void addCounter(Counter counter, SignalFxPayload payload, long timestamp) {
        payload.counters.add(new SignalFxTimeseries(counter, counter.count(), timestamp));
    }

    private void addFunctionCounter(FunctionCounter counter, SignalFxPayload payload, long timestamp) {
        payload.counters.add(new SignalFxTimeseries(counter, counter.count(), timestamp));
    }

    private void addTimer(Timer timer, SignalFxPayload payload, long timestamp) {
        HistogramSnapshot snapshot = timer.takeSnapshot(false);

        payload.counters.add(new SignalFxTimeseries(timer, "count", snapshot.count(), timestamp));
        payload.counters.add(new SignalFxTimeseries(timer, "totalTime", snapshot.total(getBaseTimeUnit()), timestamp));
        payload.gauges.add(new SignalFxTimeseries(timer, "avg", snapshot.mean(getBaseTimeUnit()), timestamp));
        payload.gauges.add(new SignalFxTimeseries(timer, "max", snapshot.max(getBaseTimeUnit()), timestamp));

        for (ValueAtPercentile v : snapshot.percentileValues()) {
            String suffix = DoubleFormat.toString(v.percentile() * 100) + "percentile";
            payload.gauges.add(new SignalFxTimeseries(timer, suffix, v.value(getBaseTimeUnit()), timestamp));
        }
    }

    private void addFunctionTimer(FunctionTimer timer, SignalFxPayload payload, long timestamp) {
        payload.counters.add(new SignalFxTimeseries(timer, "count", timer.count(), timestamp));
        payload.counters.add(new SignalFxTimeseries(timer, "totalTime", timer.totalTime(getBaseTimeUnit()), timestamp));
        payload.gauges.add(new SignalFxTimeseries(timer, "avg", timer.mean(getBaseTimeUnit()), timestamp));
    }

    private void addDistributionSummary(DistributionSummary summary, SignalFxPayload payload, long timestamp) {
        HistogramSnapshot snapshot = summary.takeSnapshot(false);

        payload.counters.add(new SignalFxTimeseries(summary, "count", snapshot.count(), timestamp));
        payload.counters.add(new SignalFxTimeseries(summary, "total", snapshot.total(), timestamp));
        payload.gauges.add(new SignalFxTimeseries(summary, "avg", snapshot.mean(), timestamp));
        payload.gauges.add(new SignalFxTimeseries(summary, "max", snapshot.max(), timestamp));

        for (ValueAtPercentile v : snapshot.percentileValues()) {
            String suffix = DoubleFormat.toString(v.percentile() * 100) + "percentile";
            payload.gauges.add(new SignalFxTimeseries(summary, suffix, v.value(), timestamp));
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    private void quietlyCloseUrlConnection(HttpURLConnection con) {
        if (con == null)
            return;
        try {
            con.disconnect();
        } catch (Exception ignore) {
        }
    }

    private class SignalFxPayload {
        Collection<SignalFxTimeseries> gauges = new ArrayList<>();
        Collection<SignalFxTimeseries> counters = new ArrayList<>();

        String toJson() {
            return "{" +
                (gauges.isEmpty() ? "" :
                    "\"gauge\":[" + gauges.stream().map(SignalFxTimeseries::toJson).collect(joining(",")) + "]"
                ) +
                (!gauges.isEmpty() && !counters.isEmpty() ? "," : "") +
                (counters.isEmpty() ? "" :
                    "\"counter\":[" + counters.stream().map(SignalFxTimeseries::toJson).collect(joining(",")) + "]"
                ) +
                "}";
        }
    }

    /**
     * {
     * "metric": "test.gauge.with_timestamp",
     * "value": 42,
     * "timestamp": 1485801354682,
     * "dimensions": { "host": "testserver" }
     * }
     */
    private class SignalFxTimeseries {
        final String metric;
        final double value;
        final long timestamp; // milliseconds since epoch
        final Collection<Tag> dimensions;

        SignalFxTimeseries(Meter meter, double value, long timestamp) {
            this(meter, null, value, timestamp);
        }

        SignalFxTimeseries(Meter meter, String statSuffix, double value, long timestamp) {
            this.metric = config().namingConvention().name(statSuffix == null ? meter.getId().getName() : meter.getId().getName() + "." + statSuffix,
                meter.getId().getType(), meter.getId().getBaseUnit());
            this.dimensions = getConventionTags(meter.getId());
            this.value = value;
            this.timestamp = timestamp;
        }

        String toJson() {
            return "{" +
                "\"metric\":\"" + metric + "\", " +
                "\"value\":" + DoubleFormat.toString(value) + "," +
                "\"timestamp\":" + timestamp +
                (dimensions.isEmpty() ? "" :
                    ",\"dimensions\":{" + dimensions.stream().map(d -> "\"" + d.getKey() + "\":\"" + d.getValue() + "\"").collect(joining(",")) + "}"
                ) +
                "}";
        }
    }
}
