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
import com.wavefront.sdk.direct.ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.proxy.WavefrontProxyClient;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.Meter.Type.match;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 * @author Howard Yoo
 */
public class WavefrontMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("wavefront-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(WavefrontMeterRegistry.class);
    private final WavefrontSender wavefrontSender;
    private final int batchSize;
    private final String source;

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
        batchSize = config.batchSize();
        source = config.source();

        URI uri = URI.create(config.uri());
        boolean sendToProxy = "proxy".equals(uri.getScheme());

        /**
         * Build a WavefrontProxyClient to handle the sending of data to a Wavefront proxy,
         * or a WavefrontDirectIngestionClient to handle the sending of data directly
         * to a Wavefront API server.
         *
         * See https://github.com/wavefrontHQ/wavefront-java-sdk/blob/master/README.md for reference.
         */
        if (sendToProxy) {
            WavefrontProxyClient.Builder proxyBuilder = new WavefrontProxyClient.Builder(uri.getHost());

            int metricsPort = uri.getPort();
            if (metricsPort != -1) proxyBuilder.metricsPort(metricsPort);

            wavefrontSender = proxyBuilder.build();
        } else {
            if (config.apiToken() == null) {
                throw new MissingRequiredConfigurationException(
                    "A token is required when publishing directly to the Wavefront API");
            }
            WavefrontDirectIngestionClient.Builder directIngestionBuilder =
                new WavefrontDirectIngestionClient
                    .Builder(config.uri(), config.apiToken());

            wavefrontSender = directIngestionBuilder.build();
        }

        config().namingConvention(new WavefrontNamingConvention(config.globalPrefix()));

        start(threadFactory);
    }

    public WavefrontMeterRegistry(WavefrontProxyConfig proxyConfig, Clock clock) {
        this(proxyConfig, clock, Executors.defaultThreadFactory());
    }

    public WavefrontMeterRegistry(WavefrontProxyConfig proxyConfig, Clock clock, ThreadFactory threadFactory) {
        super(proxyConfig, clock);
        batchSize = proxyConfig.batchSize();
        source = proxyConfig.source();

        /**
         * Build a WavefrontProxyClient to handle the sending of data to a Wavefront proxy.
         *
         * See https://github.com/wavefrontHQ/wavefront-java-sdk/blob/master/README.md for reference.
         */
        WavefrontProxyClient.Builder proxyBuilder = new WavefrontProxyClient.Builder(proxyConfig.hostName());

        Integer metricsPort = proxyConfig.metricsPort();
        if (metricsPort != null) proxyBuilder.metricsPort(metricsPort);

        Integer flushIntervalSeconds = proxyConfig.flushIntervalSeconds();
        if (flushIntervalSeconds != null) proxyBuilder.flushIntervalSeconds(flushIntervalSeconds);

        wavefrontSender = proxyBuilder.build();

        config().namingConvention(new WavefrontNamingConvention(proxyConfig.globalPrefix()));

        start(threadFactory);
    }

    public WavefrontMeterRegistry(WavefrontDirectIngestionConfig directIngestionConfig, Clock clock) {
        this(directIngestionConfig, clock, Executors.defaultThreadFactory());
    }

    public WavefrontMeterRegistry(WavefrontDirectIngestionConfig directIngestionConfig, Clock clock, ThreadFactory threadFactory) {
        super(directIngestionConfig, clock);
        batchSize = directIngestionConfig.batchSize();
        source = directIngestionConfig.source();

        /**
         * Build a WavefrontDirectIngestionClient to handle the sending of data directly to a Wavefront API server.
         *
         * See https://github.com/wavefrontHQ/wavefront-java-sdk/blob/master/README.md for reference.
         */
        if (directIngestionConfig.apiToken() == null) {
            throw new MissingRequiredConfigurationException(
                "An API token is required to publish metrics directly to Wavefront");
        }
        WavefrontDirectIngestionClient.Builder directIngestionBuilder =
            new WavefrontDirectIngestionClient
                .Builder(directIngestionConfig.uri(), directIngestionConfig.apiToken());

        Integer maxQueueSize = directIngestionConfig.maxQueueSize();
        if (maxQueueSize != null) directIngestionBuilder.maxQueueSize(maxQueueSize);

        Integer flushBatchSize = directIngestionConfig.flushBatchSize();
        if (flushBatchSize != null) directIngestionBuilder.batchSize(flushBatchSize);

        Integer flushIntervalSeconds = directIngestionConfig.flushIntervalSeconds();
        if (flushIntervalSeconds != null) directIngestionBuilder.flushIntervalSeconds(flushIntervalSeconds);

        wavefrontSender = directIngestionBuilder.build();

        config().namingConvention(new WavefrontNamingConvention(directIngestionConfig.globalPrefix()));

        start(threadFactory);
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, batchSize)) {
            batch.stream().forEach(m -> match(m,
                this::writeMeter,
                this::writeMeter,
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
        final long wallTime = clock.wallTime();

        Meter.Id id = summary.getId();
        reportMetric(id, "sum", wallTime, summary.totalAmount());
        reportMetric(id, "count", wallTime, summary.count());
        reportMetric(id, "avg", wallTime, summary.mean());
        reportMetric(id, "max", wallTime, summary.max());

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
                getConventionName(fullId), value, wallTime, source, getTagsAsMap(id)
            );
        } catch (Exception e) {
            logger.error("failed to send metric: " + getConventionName(fullId), e);
        }
    }

    private Map<String, String> getTagsAsMap(Meter.Id id) {
        return getConventionTags(id)
            .stream()
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
        private final StepRegistryConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = Executors.defaultThreadFactory();

        @SuppressWarnings("deprecation")
        Builder(WavefrontConfig config) {
            this.config = config;
        }

        public Builder(WavefrontProxyConfig proxyConfig) {
            this.config = proxyConfig;
        }

        public Builder(WavefrontDirectIngestionConfig directIngestionConfig) {
            this.config = directIngestionConfig;
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
            if (config instanceof WavefrontConfig) {
                return new WavefrontMeterRegistry((WavefrontConfig) config, clock, threadFactory);
            } else if (config instanceof WavefrontProxyConfig) {
                return new WavefrontMeterRegistry((WavefrontProxyConfig) config, clock, threadFactory);
            } else { // (config instanceof WavefrontDirectIngestionConfig)
                return new WavefrontMeterRegistry((WavefrontDirectIngestionConfig) config, clock, threadFactory);
            }
        }
    }
}
