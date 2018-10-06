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
import com.wavefront.sdk.direct_ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;
import com.wavefront.sdk.proxy.WavefrontProxyClient;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.Meter.Type.match;
import static io.micrometer.wavefront.DeltaCounter.isDeltaCounter;
import static io.micrometer.wavefront.WavefrontConstants.WAVEFRONT_METRIC_TYPE_TAG_KEY;
import static io.micrometer.wavefront.WavefrontHistogram.isWavefrontHistogram;
import static java.util.stream.StreamSupport.stream;

/**
 * Registry that builds a WavefrontSender from WavefrontConfig to handle the reporting of data
 * (e.g., metrics, DeltaCounters, WavefrontHistograms) to Wavefront.
 *
 * @author Jon Schneider
 * @author Howard Yoo
 */
public class WavefrontMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("wavefront-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(WavefrontMeterRegistry.class);
    private final WavefrontConfig config;
    private final WavefrontSender wavefrontSender;
    private Set<HistogramGranularity> histogramGranularities;

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
        super(config, clock);
        this.config = config;

        /**
         * Build a WavefrontProxyClient to handle the sending of data to a Wavefront proxy,
         * or a WavefrontDirectIngestionClient to handle the sending of data directly
         * to a Wavefront API server.
         *
         * See https://github.com/wavefrontHQ/wavefront-java-sdk/blob/master/README.md for reference.
         */
        if (config.sendToProxy()) {
            WavefrontConfig.ProxyConfig proxyConfig = config.proxyConfig();
            WavefrontProxyClient.Builder proxyBuilder =
                new WavefrontProxyClient.Builder(proxyConfig.hostName());

            Integer metricsPort = proxyConfig.metricsPort();
            if (metricsPort != null) proxyBuilder.metricsPort(metricsPort);

            Integer distributionPort = proxyConfig.distributionPort();
            if (distributionPort != null) proxyBuilder.distributionPort(distributionPort);

            Integer flushIntervalSeconds = config.flushIntervalSeconds();
            if (flushIntervalSeconds != null) proxyBuilder.flushIntervalSeconds(flushIntervalSeconds);

            wavefrontSender = proxyBuilder.build();
        } else {
            WavefrontConfig.DirectIngestionConfig directIngestionConfig =
                config.directIngestionConfig();
            if (directIngestionConfig.apiToken() == null) {
                throw new MissingRequiredConfigurationException(
                    "apiToken must be set whenever publishing directly to the Wavefront API");
            }
            WavefrontDirectIngestionClient.Builder directIngestionBuilder =
                new WavefrontDirectIngestionClient
                    .Builder(directIngestionConfig.uri(), directIngestionConfig.apiToken());

            Integer maxQueueSize = directIngestionConfig.maxQueueSize();
            if (maxQueueSize != null) directIngestionBuilder.maxQueueSize(maxQueueSize);

            Integer batchSize = directIngestionConfig.batchSize();
            if (batchSize != null) directIngestionBuilder.batchSize(batchSize);

            wavefrontSender = directIngestionBuilder.build();
        }

        histogramGranularities = new HashSet<>();
        WavefrontConfig.WavefrontHistogramConfig wavefrontHistogramConfig =
            config.wavefrontHistogramConfig();
        if (wavefrontHistogramConfig.reportMinuteDistribution()) {
            histogramGranularities.add(HistogramGranularity.MINUTE);
        }
        if (wavefrontHistogramConfig.reportHourDistribution()) {
            histogramGranularities.add(HistogramGranularity.HOUR);
        }
        if (wavefrontHistogramConfig.reportDayDistribution()) {
            histogramGranularities.add(HistogramGranularity.DAY);
        }

        config().namingConvention(new WavefrontNamingConvention(config.globalPrefix()));

        start(threadFactory);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        if (isDeltaCounter(id)) {
            // If the metric id belongs to a delta counter, create a DeltaCounter
            return new DeltaCounter(id, clock, config.step().toMillis());
        } else {
            return super.newCounter(id);
        }
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

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            batch.stream().forEach(m -> match(m,
                this::writeMeter,
                this::writeCounter,
                this::writeTimer,
                this::writeSummary,
                this::writeMeter,
                this::writeMeter,
                this::writeMeter,
                this::writeFunctionTimer,
                this::writeMeter));
        }
    }

    private boolean writeFunctionTimer(FunctionTimer timer) {
        long wallTime = clock.wallTime();

        Meter.Id id = timer.getId();

        // we can't know anything about max and percentiles originating from a function timer
        reportMetric(id, "count", wallTime, timer.count());
        reportMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit()));
        reportMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()));

        return true;
    }

    private boolean writeTimer(Timer timer) {
        final long wallTime = clock.wallTime();

        Meter.Id id = timer.getId();
        reportMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()));
        reportMetric(id, "count", wallTime, timer.count());
        reportMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit()));
        reportMetric(id, "max", wallTime, timer.max(getBaseTimeUnit()));

        return true;
    }

    private boolean writeSummary(DistributionSummary summary) {
        Meter.Id id = summary.getId();

        if (summary instanceof WavefrontHistogram) {
            reportWavefrontHistogram(id, ((WavefrontHistogram) summary).flushDistributions());
        } else {
            final long wallTime = clock.wallTime();

            reportMetric(id, "sum", wallTime, summary.totalAmount());
            reportMetric(id, "count", wallTime, summary.count());
            reportMetric(id, "avg", wallTime, summary.mean());
            reportMetric(id, "max", wallTime, summary.max());
        }

        return true;
    }

    private boolean writeCounter(Counter counter) {
        final long wallTime = clock.wallTime();

        stream(counter.measure().spliterator(), false)
            .forEach(measurement -> {
                Meter.Id id = counter.getId().withTag(measurement.getStatistic());
                if (counter instanceof DeltaCounter) {
                    reportDeltaCounter(id, measurement.getValue());
                } else {
                    reportMetric(id, null, wallTime, measurement.getValue());
                }
            });

        return true;
    }

    private boolean writeMeter(Meter meter) {
        final long wallTime = clock.wallTime();

        stream(meter.measure().spliterator(), false)
                .forEach(measurement -> {
                    Meter.Id id = meter.getId().withTag(measurement.getStatistic());
                    reportMetric(id, null, wallTime, measurement.getValue());
                });

        return true;
    }

    private void reportMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        if (value == Double.NaN) {
            return;
        }

        Meter.Id fullId = id;
        if (suffix != null) {
            fullId = idWithSuffix(id, suffix);
        }

        try {
            wavefrontSender.sendMetric(
                getConventionName(fullId), value, wallTime, config.source(), getTagsAsMap(id)
            );
        } catch (Exception e) {
            logger.error("failed to send metric: " + getConventionName(fullId), e);
        }
    }

    private void reportDeltaCounter(Meter.Id id, double value) {
        if (value == Double.NaN) {
            return;
        }

        try {
            wavefrontSender.sendDeltaCounter(
                getConventionName(id), value, config.source(), getTagsAsMap(id)
            );
        } catch (Exception e) {
            logger.error("failed to send delta counter: " + getConventionName(id), e);
        }
    }

    private void reportWavefrontHistogram(Meter.Id id,
                                          List<WavefrontHistogramImpl.Distribution> distributions) {
        String name = getConventionName(id);
        String source = config.source();
        Map<String, String> tags = getTagsAsMap(id);

        for (WavefrontHistogramImpl.Distribution distribution : distributions) {
            try {
                wavefrontSender.sendDistribution(
                    name, distribution.centroids, histogramGranularities,
                    distribution.timestamp, source, tags
                );
            } catch (Exception e) {
                logger.error("failed to send Wavefront histogram: " + name, e);
            }
        }
    }

    private Map<String, String> getTagsAsMap(Meter.Id id) {
        return getConventionTags(id)
            .stream()
            .filter(tag -> !tag.getKey().equals(WAVEFRONT_METRIC_TYPE_TAG_KEY))
            .collect(Collectors.toMap(Tag::getKey, Tag::getValue, (tag1, tag2) -> tag2));
    }

    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return new Meter.Id(
            id.getName() + "." + suffix, id.getTags(), id.getBaseUnit(), id.getDescription(), id.getType());
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
        private ThreadFactory threadFactory = Executors.defaultThreadFactory();

        @SuppressWarnings("deprecation")
        Builder(WavefrontConfig config) {
            this.config = config;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public WavefrontMeterRegistry build() {
            return new WavefrontMeterRegistry(config, clock, threadFactory);
        }
    }
}
