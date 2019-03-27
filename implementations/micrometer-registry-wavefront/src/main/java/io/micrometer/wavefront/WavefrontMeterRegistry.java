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

import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.Utils;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final ThreadFactory DEFAULT_THREAD_FACTORY =
        new NamedThreadFactory("wavefront-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(WavefrontMeterRegistry.class);
    private final WavefrontConfig config;
    private final HttpSender httpClient;
    private final URI uri;
    private final Set<HistogramGranularity> histogramGranularities;

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
        this(config, clock, threadFactory,
            new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory,
                                   HttpSender httpClient) {
        super(config, clock);
        this.config = config;
        if (directToApi() && config.apiToken() == null) {
            throw new MissingRequiredConfigurationException(
                "apiToken must be set whenever publishing directly to the Wavefront API");
        }
        this.httpClient = httpClient;
        this.uri = URI.create(config.uri());

        this.histogramGranularities = new HashSet<>();
        if (config.reportMinuteDistribution()) {
            this.histogramGranularities.add(HistogramGranularity.MINUTE);
        }
        if (config.reportHourDistribution()) {
            this.histogramGranularities.add(HistogramGranularity.HOUR);
        }
        if (config.reportDayDistribution()) {
            this.histogramGranularities.add(HistogramGranularity.DAY);
        }

        config().namingConvention(new WavefrontNamingConvention(config.globalPrefix()));

        start(threadFactory);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to Wavefront every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
                             PauseDetector pauseDetector) {
        Timer timer = new WavefrontTimer(id, clock, distributionStatisticConfig, pauseDetector,
            getBaseTimeUnit(), config.step().toMillis());
        HistogramGauges.registerWithCommonFormat(timer, this);
        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(
        Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        DistributionSummary summary = new WavefrontDistributionSummary(id, clock,
            distributionStatisticConfig, scale, config.step().toMillis());
        HistogramGauges.registerWithCommonFormat(summary, this);
        return summary;
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            Stream.Builder<String> metrics = Stream.builder();
            Stream.Builder<String> distributions = Stream.builder();
            AtomicInteger distributionCount = new AtomicInteger();

            batch.stream()
                .flatMap(m -> m.match(
                    this::writeMeter,
                    this::writeMeter,
                    this::writeTimer,
                    this::writeSummary,
                    this::writeMeter,
                    this::writeMeter,
                    this::writeMeter,
                    this::writeFunctionTimer,
                    this::writeMeter))
                .forEach(metricLineData -> {
                    if (metricLineData.isDistribution()) {
                        distributions.add(metricLineData.lineData());
                        distributionCount.getAndIncrement();
                    } else {
                        metrics.add(metricLineData.lineData());
                    }
                });

            Stream<String> metricStream = metrics.build();
            Stream<String> distributionStream = distributions.build();

            if (directToApi()) {
                flushDirectToApi(metricStream, Constants.WAVEFRONT_METRIC_FORMAT, "metrics",
                    batch.size());
                flushDirectToApi(distributionStream, Constants.WAVEFRONT_HISTOGRAM_FORMAT,
                    "distributions", distributionCount.get());
            } else {
                flushToProxy(metricStream, uri.getPort(), "metrics", batch.size());
                flushToProxy(distributionStream, config.distributionPort(), "distributions",
                    distributionCount.get());
            }
        }
    }

    private void flushDirectToApi(Stream<String> stream, String format, String description, int count) {
        if (count == 0) {
            return;
        }
        try {
            String originalPath = uri.getPath() != null && !uri.getPath().equals("/") ? uri.getPath() : "";
            httpClient.post(new URL(uri.getScheme(), uri.getHost(), uri.getPort(), originalPath + "/report?f=" + format).toString())
                .withHeader("Authorization", "Bearer " + config.apiToken())
                .withContent("application/octet-stream", stream.collect(joining()))
                .compress()
                .send()
                .onSuccess(response -> logSuccessfulMetricsSent(description, count))
                .onError(response -> logger.error("failed to send {} to Wavefront: {}", description, response.body()));
        } catch (Throwable e) {
            logger.error("failed to send " + description + " to Wavefront", e);
        }
    }

    @SuppressWarnings("deprecation")
    private void flushToProxy(Stream<String> stream, int port, String description, int count) {
        if (count == 0) {
            return;
        }
        try {
            SocketAddress endpoint = uri.getHost() != null ?
                new InetSocketAddress(uri.getHost(), port) :
                new InetSocketAddress(InetAddress.getByName(null), port);
            try (Socket socket = new Socket()) {
                // connectTimeout should be pulled up to WavefrontConfig when it is removed elsewhere
                socket.connect(endpoint, (int) this.config.connectTimeout().toMillis());
                try (OutputStreamWriter writer =
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(stream.collect(joining()));
                    writer.flush();
                }
                logSuccessfulMetricsSent(description, count);
            } catch (IOException e) {
                logger.error("failed to send " + description + " to Wavefront", e);
            }
        } catch (UnknownHostException e) {
            logger.error("failed to send " + description + " to Wavefront: unknown host + " + uri.getHost());
        }
    }

    private void logSuccessfulMetricsSent(String description, int count) {
        logger.debug("successfully sent {} {} to Wavefront.", count, description);
    }

    private boolean directToApi() {
        return !"proxy".equals(URI.create(config.uri()).getScheme());
    }

    private Stream<WavefrontMetricLineData> writeFunctionTimer(FunctionTimer timer) {
        long wallTime = clock.wallTime();
        Stream.Builder<WavefrontMetricLineData> metrics = Stream.builder();

        Meter.Id id = timer.getId();

        // we can't know anything about max and percentiles originating from a function timer
        addMetric(metrics, id, "count", wallTime, timer.count());
        addMetric(metrics, id, "avg", wallTime, timer.mean(getBaseTimeUnit()));
        addMetric(metrics, id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()));

        return metrics.build();
    }

    private Stream<WavefrontMetricLineData> writeTimer(Timer timer) {
        final long wallTime = clock.wallTime();
        final Stream.Builder<WavefrontMetricLineData> metrics = Stream.builder();

        Meter.Id id = timer.getId();

        addMetric(metrics, id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()));
        addMetric(metrics, id, "count", wallTime, timer.count());
        addMetric(metrics, id, "avg", wallTime, timer.mean(getBaseTimeUnit()));
        addMetric(metrics, id, "max", wallTime, timer.max(getBaseTimeUnit()));
        addDistribution(metrics, id, ((WavefrontTimer) timer).flushDistributions());
        return metrics.build();
    }

    private Stream<WavefrontMetricLineData> writeSummary(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final Stream.Builder<WavefrontMetricLineData> metrics = Stream.builder();

        Meter.Id id = summary.getId();

        addMetric(metrics, id, "sum", wallTime, summary.totalAmount());
        addMetric(metrics, id, "count", wallTime, summary.count());
        addMetric(metrics, id, "avg", wallTime, summary.mean());
        addMetric(metrics, id, "max", wallTime, summary.max());
        addDistribution(metrics, id, ((WavefrontDistributionSummary) summary).flushDistributions());

        return metrics.build();
    }

    private Stream<WavefrontMetricLineData> writeMeter(Meter meter) {
        long wallTime = clock.wallTime();
        Stream.Builder<WavefrontMetricLineData> metrics = Stream.builder();

        stream(meter.measure().spliterator(), false)
            .forEach(measurement -> {
                Meter.Id id = meter.getId().withTag(measurement.getStatistic());
                addMetric(metrics, id, null, wallTime, measurement.getValue());
            });

        return metrics.build();
    }

    // VisibleForTesting
    void addMetric(Stream.Builder<WavefrontMetricLineData> metrics, Meter.Id id, @Nullable String suffix,
                   long wallTime, double value) {
        if (!Double.isFinite(value)) {
            return;
        }

        Meter.Id fullId = id;
        if (suffix != null) {
            fullId = idWithSuffix(id, suffix);
        }

        String name = getConventionName(fullId);
        String source = config.source();
        Map<String, String> tags = getTagsAsMap(id);

        try {
            String lineData = Utils.metricToLineData(name, value, wallTime, source, tags, "unknown");
            metrics.add(new WavefrontMetricLineData(lineData, false));
        } catch (IllegalArgumentException e) {
            logger.error("failed to convert metric to Wavefront format: " + fullId.getName(), e);
        }
    }

    // VisibleForTesting
    void addDistribution(Stream.Builder<WavefrontMetricLineData> metrics, Meter.Id id,
                         List<WavefrontHistogramImpl.Distribution> distributions) {
        String name = getConventionName(id);
        String source = config.source();
        Map<String, String> tags = getTagsAsMap(id);

        for (WavefrontHistogramImpl.Distribution distribution : distributions) {
            try {
                String lineData = Utils.histogramToLineData(name, distribution.centroids,
                    histogramGranularities, distribution.timestamp, source, tags, "unknown");
                metrics.add(new WavefrontMetricLineData(lineData, true));
            } catch (IllegalArgumentException e) {
                logger.error("failed to convert distribution to Wavefront format: " + id.getName(), e);
            }
        }
    }

    private Map<String, String> getTagsAsMap(Meter.Id id) {
        return getConventionTags(id)
            .stream()
            .collect(Collectors.toMap(Tag::getKey, Tag::getValue, (tag1, tag2) -> tag2));
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
