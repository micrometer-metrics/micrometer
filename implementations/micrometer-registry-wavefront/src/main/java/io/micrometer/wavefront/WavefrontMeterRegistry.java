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
package io.micrometer.wavefront;

import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
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
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.wavefront.sdk.common.Constants.*;
import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
import static io.micrometer.wavefront.WavefrontHistogram.isWavefrontHistogram;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 * @author Howard Yoo
 */
public class WavefrontMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("wavefront-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(WavefrontMeterRegistry.class);
    private final WavefrontConfig config;
    @Nullable
    private final HttpSender httpClient;
    @Nullable
    private final WavefrontSender wavefrontSender;
    @Nullable
    private final Tags globalTags;
    @Nullable
    private final Set<HistogramGranularity> histogramGranularities;

    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clock  The clock to use for timings.
     */
    @SuppressWarnings("deprecation")
    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
            new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()), null);
    }

    /**
     * @param config        Configuration options for the registry that are describable as properties.
     * @param clock         The clock to use for timings.
     * @param threadFactory The thread factory to use to create the publishing thread.
     * @deprecated Use {@link #builder(WavefrontConfig)} instead.
     */
    @Deprecated
    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()),
            null);
    }

    private WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory,
                                   HttpSender httpClient, @Nullable Tags globalTags) {
        this(config, clock, httpClient, null, null, globalTags);
        if (directToApi() && config.apiToken() == null) {
            throw new MissingRequiredConfigurationException("apiToken must be set whenever publishing directly to the Wavefront API");
        }
        start(threadFactory);
    }

    private WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory,
                                   WavefrontSender wavefrontSender, Set<HistogramGranularity> histogramGranularities,
                                   @Nullable Tags globalTags) {
        this(config, clock, (HttpSender) null, wavefrontSender, histogramGranularities, globalTags);
        start(threadFactory);
    }

    private WavefrontMeterRegistry(WavefrontConfig config, Clock clock, @Nullable HttpSender httpClient,
                                   @Nullable WavefrontSender wavefrontSender,
                                   @Nullable Set<HistogramGranularity> histogramGranularities,
                                   @Nullable Tags globalTags) {
        super(config, clock);
        this.config = config;
        this.httpClient = httpClient;
        this.wavefrontSender = wavefrontSender;
        this.globalTags = globalTags;
        this.histogramGranularities = histogramGranularities;

        config().namingConvention(new WavefrontNamingConvention(config.globalPrefix()));
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to wavefront every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    /**
     * Measures the distribution of samples using Wavefront's histogram implementation.
     *
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing Wavefront histogram.
     */
    public WavefrontHistogram wavefrontHistogram(String name, Iterable<Tag> tags) {
        return WavefrontHistogram.builder(name).tags(tags).register(this);
    }

    /**
     * Measures the distribution of samples using Wavefront's histogram implementation.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing Wavefront histogram.
     */
    public WavefrontHistogram wavefrontHistogram(String name, String... tags) {
        return wavefrontHistogram(name, Tags.of(tags));
    }

    @Override
    protected DistributionSummary newDistributionSummary(
        Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        if (isWavefrontHistogram(id)) {
            // If the metric id belongs to a Wavefront histogram, create a WavefrontHistogram
            return new WavefrontHistogram(id, clock, distributionStatisticConfig, scale);
        } else {
            return super.newDistributionSummary(id, distributionStatisticConfig, scale);
        }
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

            if (wavefrontSender != null) {
                // metrics are sent to Wavefront using the wavefrontSender
                stream.collect(joining(" "));
                return;
            }

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

        if (summary instanceof WavefrontHistogram) {
            if (wavefrontSender != null) {
                sendWavefrontHistogram(id, ((WavefrontHistogram) summary).flushDistributions());
            } else {
                logger.info("unable to send WavefrontHistogram without WavefrontSender");
            }
        } else {
            addMetric(metrics, id, "sum", wallTime, summary.totalAmount());
            addMetric(metrics, id, "count", wallTime, summary.count());
            addMetric(metrics, id, "avg", wallTime, summary.mean());
            addMetric(metrics, id, "max", wallTime, summary.max());
        }

        return metrics.build();
    }

    private Stream<String> writeMeter(Meter meter) {
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
            if (wavefrontSender != null) {
                // if wavefrontSender is available, use it to send the metric
                sendMetric(id, suffix, wallTime, value);
            } else {
                metrics.add(writeMetric(id, suffix, wallTime, value));
            }
        }
    }

    private void sendMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        try {
            wavefrontSender.sendMetric(getConventionName(fullId), value, wallTime, config.source(),
                getTagsAsMap(id));
        } catch (Exception e) {
            logger.error("failed to send metric to Wavefront: " + getConventionName(fullId), e);
        }
    }

    private void sendWavefrontHistogram(Meter.Id id,
                                        List<WavefrontHistogramImpl.Distribution> distributions) {
        String name = getConventionName(id);
        String source = config.source();
        Map<String, String> tags = getTagsAsMap(id);

        for (WavefrontHistogramImpl.Distribution distribution : distributions) {
            try {
                wavefrontSender.sendDistribution(name, distribution.centroids,
                    histogramGranularities, distribution.timestamp, source, tags);
            } catch (Exception e) {
                logger.error("failed to send Wavefront histogram: " + name, e);
            }
        }
    }

    private Map<String, String> getTagsAsMap(Meter.Id id) {
        return getConventionTags(id)
            .stream()
            .collect(Collectors.toMap(Tag::getKey, Tag::getValue, (tag1, tag2) -> tag2));
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

    @Override
    protected List<Tag> getConventionTags(Meter.Id id) {
        Iterable<Tag> tags = Tags.concat(id.getTagsAsIterable(), globalTags);
        return StreamSupport.stream(tags.spliterator(), false)
            .map(t -> Tag.of(config().namingConvention().tagKey(t.getKey()),
                config().namingConvention().tagValue(t.getValue())))
            .collect(Collectors.toList());
    }

    public static Builder builder(WavefrontConfig config) {
        return new Builder(config);
    }

    public static class Builder {
        private final WavefrontConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;
        @Nullable
        private ApplicationTags applicationTags;
        private Set<HistogramGranularity> histogramGranularities;

        @SuppressWarnings("deprecation")
        Builder(WavefrontConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout());
            this.applicationTags = null;
            this.histogramGranularities = new HashSet<>();
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

        /**
         * @param applicationTags   {@link ApplicationTags} containing metadata about your application that get reported to Wavefront as tags.
         * @return The Wavefront registry builder with added {@link ApplicationTags}.
         */
        public Builder applicationTags(ApplicationTags applicationTags) {
            this.applicationTags = applicationTags;
            return this;
        }

        /**
         * @return The Wavefront registry builder with aggregating of {@link WavefrontHistogram} by minute intervals enabled.
         */
        public Builder reportMinuteDistribution() {
            histogramGranularities.add(HistogramGranularity.MINUTE);
            return this;
        }

        /**
         * @return The Wavefront registry builder with aggregating of {@link WavefrontHistogram} by hour intervals enabled.
         */
        public Builder reportHourDistribution() {
            histogramGranularities.add(HistogramGranularity.HOUR);
            return this;
        }

        /**
         * @return The Wavefront registry builder with aggregating of {@link WavefrontHistogram} by day intervals enabled.
         */
        public Builder reportDayDistribution() {
            histogramGranularities.add(HistogramGranularity.DAY);
            return this;
        }

        @Nullable
        private Tags globalTags() {
            if (applicationTags == null) {
                return null;
            }
            Tags globalTags = Tags.of(
                APPLICATION_TAG_KEY, applicationTags.getApplication(),
                SERVICE_TAG_KEY, applicationTags.getService(),
                CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster(),
                SHARD_TAG_KEY, applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard()
            );
            if (applicationTags.getCustomTags() != null) {
                globalTags = globalTags.and(applicationTags.getCustomTags().entrySet().stream()
                    .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList()));
            }
            return globalTags;
        }

        public WavefrontMeterRegistry build() {
            return new WavefrontMeterRegistry(config, clock, threadFactory, httpClient, globalTags());
        }

        /**
         * Builds a {@link WavefrontMeterRegistry} that sends data to Wavefront using a {@link WavefrontSender}.
         * Note that {@link WavefrontHistogram}s can only be sent using a registry that sends data via a {@link WavefrontSender}.
         *
         * @param wavefrontSender   A {@link WavefrontSender} that sends data to Wavefront.
         * @return A new Wavefront registry that reports to Wavefront using the specified {@link WavefrontSender}.
         */
        public WavefrontMeterRegistry build(WavefrontSender wavefrontSender) {
            return new WavefrontMeterRegistry(config, clock, threadFactory, wavefrontSender,
                histogramGranularities, globalTags());
        }
    }
}
