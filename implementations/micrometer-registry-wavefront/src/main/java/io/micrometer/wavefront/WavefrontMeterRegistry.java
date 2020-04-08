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

import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.direct.ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.cumulative.CumulativeCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

/**
 * {@link PushMeterRegistry} for Wavefront.
 *
 * @author Jon Schneider
 * @author Howard Yoo
 * @since 1.0.0
 */
public class WavefrontMeterRegistry extends PushMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY =
        new NamedThreadFactory("waveferont-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(WavefrontMeterRegistry.class);
    private final WavefrontConfig config;
    private final WavefrontSender wavefrontSender;
    private final Set<HistogramGranularity> histogramGranularities;

    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clock  The clock to use for timings.
     */
    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, getDefaultSenderBuilder(config).build());
    }

    /**
     * @param config        Configuration options for the registry that are describable as properties.
     * @param clock         The clock to use for timings.
     * @param threadFactory The thread factory to use to create the publishing thread.
     * @deprecated Use {@link #builder(WavefrontConfig)} instead.
     */
    @Deprecated
    public WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, getDefaultSenderBuilder(config).build());
    }

    WavefrontMeterRegistry(WavefrontConfig config, Clock clock, ThreadFactory threadFactory,
                           WavefrontSender wavefrontSender) {
        super(config, clock);
        this.config = config;
        if (config.uri() == null)
            throw new MissingRequiredConfigurationException("A uri is required to publish metrics to Wavefront");
        if (isDirectToApi(config) && config.apiToken() == null) {
            throw new MissingRequiredConfigurationException(
                    "apiToken must be set whenever publishing directly to the Wavefront API");
        }
        this.wavefrontSender = wavefrontSender;

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

    private static boolean isDirectToApi(WavefrontConfig config) {
        return !"proxy".equals(URI.create(config.uri()).getScheme());
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new CumulativeCounter(id);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return new DefaultLongTaskTimer(id, clock);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
                             PauseDetector pauseDetector) {
        WavefrontTimer timer = new WavefrontTimer(id, clock, distributionStatisticConfig, pauseDetector,
            getBaseTimeUnit());
        if (!timer.isPublishingHistogram()) {
            HistogramGauges.registerWithCommonFormat(timer, this);
        }
        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(
        Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        WavefrontDistributionSummary summary = new WavefrontDistributionSummary(id, clock,
            distributionStatisticConfig, scale);
        if (!summary.isPublishingHistogram()) {
            HistogramGauges.registerWithCommonFormat(summary, this);
        }
        return summary;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new CumulativeFunctionCounter<>(id, obj, countFunction);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected void publish() {
        getMeters().forEach(m -> m.use(
                this::publishMeter,
                this::publishMeter,
                this::publishTimer,
                this::publishSummary,
                this::publishMeter,
                this::publishMeter,
                this::publishMeter,
                this::publishFunctionTimer,
                this::publishMeter));
    }

    private void publishFunctionTimer(FunctionTimer timer) {
        long wallTime = clock.wallTime();

        Meter.Id id = timer.getId();

        // we can't know anything about max and percentiles originating from a function timer
        publishMetric(id, "count", wallTime, timer.count());
        publishMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit()));
        publishMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()));
    }

    private void publishTimer(Timer timer) {
        final long wallTime = clock.wallTime();

        final Meter.Id id = timer.getId();
        final WavefrontTimer wfTimer = (WavefrontTimer) timer;

        if (wfTimer.isPublishingHistogram()) {
            publishDistribution(id, wfTimer.flushDistributions());
        } else {
            publishMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()));
            publishMetric(id, "count", wallTime, timer.count());
            publishMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit()));
            publishMetric(id, "max", wallTime, timer.max(getBaseTimeUnit()));
        }
    }

    private void publishSummary(DistributionSummary summary) {
        final long wallTime = clock.wallTime();

        final Meter.Id id = summary.getId();
        final WavefrontDistributionSummary wfSummary = (WavefrontDistributionSummary) summary;

        if (wfSummary.isPublishingHistogram()) {
            publishDistribution(id, wfSummary.flushDistributions());
        } else {
            publishMetric(id, "sum", wallTime, summary.totalAmount());
            publishMetric(id, "count", wallTime, summary.count());
            publishMetric(id, "avg", wallTime, summary.mean());
            publishMetric(id, "max", wallTime, summary.max());
        }
    }

    // VisibleForTesting
    void publishMeter(Meter meter) {
        long wallTime = clock.wallTime();

        stream(meter.measure().spliterator(), false)
            .forEach(measurement -> {
                Meter.Id id = meter.getId().withTag(measurement.getStatistic());
                publishMetric(id, null, wallTime, measurement.getValue());
            });
    }

    // VisibleForTesting
    void publishMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
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
            wavefrontSender.sendMetric(name, value, wallTime, source, tags);
        } catch (IOException e) {
            logger.warn("failed to report metric to Wavefront: " + fullId.getName(), e);
        }
    }

    // VisibleForTesting
    void publishDistribution(Meter.Id id, List<WavefrontHistogramImpl.Distribution> distributions) {
        String name = getConventionName(id);
        String source = config.source();
        Map<String, String> tags = getTagsAsMap(id);

        for (WavefrontHistogramImpl.Distribution distribution : distributions) {
            try {
                wavefrontSender.sendDistribution(name, distribution.centroids, histogramGranularities,
                        distribution.timestamp, source, tags);
            } catch (IOException e) {
                logger.warn("failed to send distribution to Wavefront: " + id.getName(), e);
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

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(config.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }
  
    static String getWavefrontReportingUri(WavefrontConfig wavefrontConfig) {
        // proxy reporting is now http reporting on newer wavefront proxies.
        if (!isDirectToApi(wavefrontConfig)) {
            return "http" + wavefrontConfig.uri().substring("proxy".length());
        }
        return wavefrontConfig.uri();
    }

    /**
     * Creates a Builder for the default {@link WavefrontSender} to be used with a
     * {@link WavefrontMeterRegistry} if one is not provided. Generates the builder
     * based on the given {@link WavefrontConfig}.
     *
     * @param config config to use
     * @return a builder for a WavefrontSender
     * @since 1.5.0
     */
    public static WavefrontDirectIngestionClient.Builder getDefaultSenderBuilder(WavefrontConfig config) {
        return new WavefrontDirectIngestionClient.Builder(getWavefrontReportingUri(config),
                config.apiToken());
    }

    public static Builder builder(WavefrontConfig config) {
        return new Builder(config);
    }

    public static class Builder {
        private final WavefrontConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        @Nullable
        private WavefrontSender wavefrontSender;

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

        /**
         * @return builder
         * @deprecated since 1.5.0 this call no-longer affects the transport used to send metrics to Wavefront. Use
         * {@link #wavefrontSender(WavefrontSender)} to supply your own transport (whether proxy or direct ingestion).
         */
        @Deprecated
        public Builder httpClient(@SuppressWarnings("unused") HttpSender httpClient) {
            return this;
        }

        /**
         * @param wavefrontSender wavefront sender to be used
         * @return builder
         * @since 1.5.0
         */
        public Builder wavefrontSender(WavefrontSender wavefrontSender) {
            this.wavefrontSender = wavefrontSender;
            return this;
        }

        public WavefrontMeterRegistry build() {
            if (wavefrontSender == null) {
                return new WavefrontMeterRegistry(config, clock, threadFactory);
            }
            return new WavefrontMeterRegistry(config, clock, threadFactory, wavefrontSender);
        }
    }
}
