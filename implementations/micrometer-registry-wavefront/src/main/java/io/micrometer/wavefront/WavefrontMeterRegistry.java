/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.wavefront;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link StepMeterRegistry} for Wavefront.
 *
 * @author Jon Schneider
 * @author Howard Yoo
 * @since 1.0.0
 */
public class WavefrontMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("wavefront-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(WavefrontMeterRegistry.class);
    private final WavefrontConfig config;
    private final HttpSender httpClient;

    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clock  The clock to use for timings.
     */
    @SuppressWarnings("deprecation")
    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    /**
     * @param config        Configuration options for the registry that are describable as properties.
     * @param clock         The clock to use for timings.
     * @param threadFactory The thread factory to use to create the publishing thread.
     * @deprecated Use {@link #builder(WavefrontConfig)} instead.
     */
    @Deprecated
    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        this.config = config;
        this.httpClient = httpClient;
        if (directToApi() && config.apiToken() == null) {
            throw new MissingRequiredConfigurationException("apiToken must be set whenever publishing directly to the Wavefront API");
        }

        config().namingConvention(new WavefrontNamingConvention(config.globalPrefix()));

        start(threadFactory);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to wavefront every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            Stream<String> stream = batch.stream().flatMap(m -> m.match(
                    this::writeMeter,
                    this::writeMeter,
                    this::writeTimer,
                    this::writeSummary,
                    this::writeMeter,
                    this::writeMeter,
                    this::writeMeter,
                    this::writeFunctionTimer,
                    this::writeMeter));

            if (directToApi()) {
                try {
                    httpClient.post(config.uri() + "/report/metrics?t=" + config.apiToken() + "&h=" + config.source())
                            .acceptJson()
                            .withJsonContent("{" + stream.collect(joining(",")) + "}")
                            .send()
                            .onSuccess(response -> logSuccessfulMetricsSent(batch))
                            .onError(response -> logger.error("failed to send metrics to wavefront: {}", response.body()));
                } catch (Throwable e) {
                    logger.error("failed to send metrics to wavefront", e);
                }
            } else {
                URI uri = URI.create(config.uri());
                try {
                    SocketAddress endpoint = uri.getHost() != null ? new InetSocketAddress(uri.getHost(), uri.getPort()) :
                            new InetSocketAddress(InetAddress.getByName(null), uri.getPort());
                    try (Socket socket = new Socket()) {
                        // connectTimeout should be pulled up to WavefrontConfig when it is removed elsewhere
                        socket.connect(endpoint, (int) this.config.connectTimeout().toMillis());
                        try (OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
                            writer.write(stream.collect(joining("\n")) + "\n");
                            writer.flush();
                        }
                        logSuccessfulMetricsSent(batch);
                    } catch (IOException e) {
                        logger.error("failed to send metrics to wavefront", e);
                    }
                } catch (UnknownHostException e) {
                    logger.error("failed to send metrics to wavefront: unknown host + " + uri.getHost());
                }
            }
        }
    }

    private void logSuccessfulMetricsSent(List<Meter> batch) {
        logger.debug("successfully sent {} metrics to Wavefront.", batch.size());
    }

    private boolean directToApi() {
        return !"proxy".equals(URI.create(config.uri()).getScheme());
    }

    private Stream<String> writeFunctionTimer(FunctionTimer timer) {
        long wallTime = clock.wallTime();
        Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = timer.getId();

        // we can't know anything about max and percentiles originating from a function timer
        addMetric(metrics, id, "count", wallTime, timer.count());
        addMetric(metrics, id, "avg", wallTime, timer.mean(getBaseTimeUnit()));
        addMetric(metrics, id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()));

        return metrics.build();
    }

    private Stream<String> writeTimer(Timer timer) {
        final long wallTime = clock.wallTime();
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = timer.getId();
        addMetric(metrics, id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()));
        addMetric(metrics, id, "count", wallTime, timer.count());
        addMetric(metrics, id, "avg", wallTime, timer.mean(getBaseTimeUnit()));
        addMetric(metrics, id, "max", wallTime, timer.max(getBaseTimeUnit()));

        return metrics.build();
    }

    private Stream<String> writeSummary(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = summary.getId();
        addMetric(metrics, id, "sum", wallTime, summary.totalAmount());
        addMetric(metrics, id, "count", wallTime, summary.count());
        addMetric(metrics, id, "avg", wallTime, summary.mean());
        addMetric(metrics, id, "max", wallTime, summary.max());

        return metrics.build();
    }

    // VisibleForTesting
    Stream<String> writeMeter(Meter meter) {
        long wallTime = clock.wallTime();
        Stream.Builder<String> metrics = Stream.builder();

        stream(meter.measure().spliterator(), false)
                .forEach(measurement -> {
                    Meter.Id id = meter.getId().withTag(measurement.getStatistic());
                    addMetric(metrics, id, null, wallTime, measurement.getValue());
                });

        return metrics.build();
    }

    // VisibleForTesting
    void addMetric(Stream.Builder<String> metrics, Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        if (Double.isFinite(value)) {
            metrics.add(writeMetric(id, suffix, wallTime, value));
        }
    }

    /**
     * The metric format is a little different depending on whether you are going straight to the
     * Wavefront API server or through a sidecar proxy.
     * <p>
     * https://docs.wavefront.com/wavefront_data_format.html#wavefront-data-format-syntax
     */
    private String writeMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        return directToApi() ?
                writeMetricDirect(id, suffix, value) :
                writeMetricProxy(id, suffix, wallTime, value);
    }

    private String writeMetricProxy(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        // surrounding the name with double quotes allows for / and , in names
        return "\"" + getConventionName(fullId) + "\" " + DoubleFormat.decimalOrNan(value) + " " + (wallTime / 1000) +
                " source=" + config.source() + " " +
                getConventionTags(fullId)
                        .stream()
                        .map(t -> t.getKey() + "=\"" + t.getValue() + "\"")
                        .collect(joining(" "));
    }

    private String writeMetricDirect(Meter.Id id, @Nullable String suffix, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        List<Tag> conventionTags = getConventionTags(fullId);

        String tags = conventionTags
                .stream()
                .map(t -> "\"" + escapeJson(t.getKey()) + "\": \"" + escapeJson(t.getValue()) + "\"")
                .collect(joining(","));

        UUID uuid = UUID.randomUUID();
        String uniqueNameSuffix = ((Long) uuid.getMostSignificantBits()).toString() + uuid.getLeastSignificantBits();

        // To be valid JSON, the metric name must be unique. Since the same name can occur in multiple entries because of
        // variance in tag values, we need to append a suffix to the name. The suffix must be numeric, or Wavefront interprets
        // it as part of the name. Wavefront strips a $<NUMERIC> suffix from the name at parsing time.
        return "\"" + escapeJson(getConventionName(fullId)) + "$" + uniqueNameSuffix + "\"" +
                ": {" +
                "\"value\": " + DoubleFormat.decimalOrNan(value) + "," +
                "\"tags\": {" + tags + "}" +
                "}";
    }

    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    public static Builder builder(WavefrontConfig config) {
        return new Builder(config);
    }

    public static class Builder {
        private final WavefrontConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(WavefrontConfig config) {
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

        public WavefrontMeterRegistry build() {
            return new WavefrontMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}
