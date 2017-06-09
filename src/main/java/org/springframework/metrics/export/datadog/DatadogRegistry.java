package org.springframework.metrics.export.datadog;

import com.netflix.spectator.api.*;
import com.netflix.spectator.impl.Scheduler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

/**
 * Registry for reporting metrics to Datadog.
 *
 * @author Jon Schneider
 */
public final class DatadogRegistry extends AbstractRegistry {

    private final Clock clock;

    private final boolean enabled;
    private final Duration step;
    private final long stepMillis;
    private final URL metricsEndpoint;

    private final int connectTimeout;
    private final int readTimeout;
    private final int batchSize;
    private final int numThreads;

    private final String hostTag;

    private Scheduler scheduler;

    public DatadogRegistry(Clock clock, DatadogConfig config) {
        super(new StepClock(clock, config.step().toMillis()), config);
        this.clock = clock;

        this.enabled = config.enabled();
        this.step = config.step();
        this.stepMillis = step.toMillis();

        try {
            this.metricsEndpoint = URI.create("https://app.datadoghq.com/api/v1/series?api_key=" + config.apiKey()).toURL();
        } catch (MalformedURLException e) {
            // not possible
            throw new RuntimeException(e);
        }

        this.connectTimeout = (int) config.connectTimeout().toMillis();
        this.readTimeout = (int) config.readTimeout().toMillis();
        this.batchSize = config.batchSize();
        this.numThreads = config.numThreads();

        this.hostTag = config.hostTag();
    }

    /**
     * Start the scheduler to collect metrics data.
     */
    public void start() {
        if (scheduler == null) {
            // Setup main collection for publishing to Atlas
            if (enabled) {
                Scheduler.Options options = new Scheduler.Options()
                        .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, step)
                        .withInitialDelay(Duration.ofMillis(getInitialDelay(stepMillis)))
                        .withStopOnFailure(false);
                scheduler = new Scheduler(this, "spring-metrics-datadog", numThreads);
                scheduler.schedule(options, this::collectData);
                logger.info("started collecting metrics every {} reporting to {}", step, metricsEndpoint);
            } else {
                logger.info("publishing is not enabled");
            }
        } else {
            logger.warn("registry already started, ignoring duplicate request");
        }
    }

    /**
     * Avoid collecting right on boundaries to minimize transitions on step longs
     * during a collection. Randomly distribute across the middle of the step interval.
     */
    private long getInitialDelay(long stepSize) {
        long now = clock.wallTime();
        long stepBoundary = now / stepSize * stepSize;

        // Buffer by 10% of the step interval on either side
        long offset = stepSize / 10;

        // Check if the current delay is within the acceptable range
        long delay = now - stepBoundary;
        if (delay < offset) {
            return delay + offset;
        } else if (delay > stepSize - offset) {
            return stepSize - offset;
        } else {
            return delay;
        }
    }

    /**
     * Stop the scheduler reporting Datadog metrics.
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
            logger.info("stopped collecting metrics every {}ms reporting to {}", step, metricsEndpoint);
        } else {
            logger.warn("registry stopped, but was never started");
        }
    }

    private void collectData() {
        if (enabled) {
            try {
                for (List<Measurement> batch : getBatches()) {
                    HttpURLConnection con = (HttpURLConnection) metricsEndpoint.openConnection();
                    con.setConnectTimeout(connectTimeout);
                    con.setReadTimeout(readTimeout);
                    con.setRequestMethod("POST");
                    con.setRequestProperty("Content-Type", "application/json");
                    con.setDoOutput(true);

                    /*
                    Example post body from Datadog API docs. Type seems to be irrelevant. Host and tags are optional.
                    "{ \"series\" :
                            [{\"metric\":\"test.metric\",
                              \"points\":[[$currenttime, 20]],
                              \"type\":\"gauge\",
                              \"host\":\"test.example.com\",
                              \"tags\":[\"environment:test\"]}
                            ]
                    }"
                    */

                    String body = "{\"series\":[" +
                            batch.stream().map(m -> {
                                Iterable<Tag> tags = m.id().tags();

                                String host = hostTag == null ? "" : StreamSupport.stream(tags.spliterator(), false)
                                        .filter(t -> hostTag.equals(t.key()))
                                        .findAny()
                                        .map(t -> ",\"host\":" + t.value())
                                        .orElse("");

                                String tagsArray = tags.iterator().hasNext() ?
                                        ",\"tags\":[" +
                                                StreamSupport.stream(tags.spliterator(), false)
                                                        .map(t -> "\"" + t.key() + ":" + t.value() + "\"")
                                                        .collect(joining(",")) + "]" : "";

                                return "{\"metric\":\"" + m.id().name() + "\"," +
                                        "\"points\":[[" + (m.timestamp() / 1000) + ", " + m.value() + "]]" +
                                        host + tagsArray +
                                        "}";
                            }).collect(joining(",")) +
                            "]}";

                    OutputStream os = con.getOutputStream();
                    os.write(body.getBytes());
                    os.flush();

                    int status = con.getResponseCode();

                    if (status >= 400) {
                        try (InputStream in = (status >= 400) ? con.getErrorStream() : con.getInputStream()) {
                            logger.error("failed to send metrics: " + new BufferedReader(new InputStreamReader(in))
                                    .lines().collect(joining("\n")));
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("failed to send metrics", e);
            }
        }
    }

    /**
     * Get a list of all measurements from the registry.
     */
    List<Measurement> getMeasurements() {
        return stream()
                .flatMap(m -> StreamSupport.stream(m.measure().spliterator(), false))
                .collect(Collectors.toList());
    }

    /**
     * Get a list of all measurements and break them into batches.
     */
    private List<List<Measurement>> getBatches() {
        List<List<Measurement>> batches = new ArrayList<>();
        List<Measurement> ms = getMeasurements();
        for (int i = 0; i < ms.size(); i += batchSize) {
            List<Measurement> batch = ms.subList(i, Math.min(ms.size(), i + batchSize));
            batches.add(batch);
        }
        return batches;
    }

    @Override
    protected Counter newCounter(Id id) {
        return new DatadogCounter(id, clock, stepMillis);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Id id) {
        return new DatadogDistributionSummary(id, clock, stepMillis);
    }

    @Override
    protected Timer newTimer(Id id) {
        return new DatadogTimer(id, clock, stepMillis);
    }

    @Override
    protected Gauge newGauge(Id id) {
        // Be sure to get StepClock so the measurements will have step aligned
        // timestamps.
        return new DatadogGauge(id, clock());
    }
}
