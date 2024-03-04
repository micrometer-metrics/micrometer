/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.dynatrace;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.dynatrace.types.DynatraceDistributionSummary;
import io.micrometer.dynatrace.types.DynatraceLongTaskTimer;
import io.micrometer.dynatrace.types.DynatraceTimer;
import io.micrometer.dynatrace.v1.DynatraceExporterV1;
import io.micrometer.dynatrace.v2.DynatraceExporterV2;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static io.micrometer.core.instrument.config.MeterFilterReply.DENY;
import static io.micrometer.core.instrument.config.MeterFilterReply.NEUTRAL;

/**
 * {@link StepMeterRegistry} for Dynatrace.
 *
 * @author Oriol Barcelona
 * @author Jon Schneider
 * @author Johnny Lim
 * @author PJ Fanning
 * @author Georg Pirklbauer
 * @author Jonatan Ivanov
 * @since 1.1.0
 */
public class DynatraceMeterRegistry extends StepMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("dynatrace-metrics-publisher");

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DynatraceMeterRegistry.class);

    private final boolean useDynatraceSummaryInstruments;

    private final boolean shouldAddZeroPercentile;

    private final DenyZeroPercentileMeterFilter zeroPercentileMeterFilter = new DenyZeroPercentileMeterFilter();

    private final AbstractDynatraceExporter exporter;

    @SuppressWarnings("deprecation")
    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private DynatraceMeterRegistry(DynatraceConfig config, Clock clock, ThreadFactory threadFactory,
            HttpSender httpClient) {
        super(config, clock);

        useDynatraceSummaryInstruments = config.useDynatraceSummaryInstruments();
        // zero percentile is needed only when using the V2 exporter and not using
        // Dynatrace summary instruments.
        shouldAddZeroPercentile = config.apiVersion() == DynatraceApiVersion.V2 && !useDynatraceSummaryInstruments;

        if (config.apiVersion() == DynatraceApiVersion.V2) {
            logger.info("Exporting to Dynatrace metrics API v2");
            this.exporter = new DynatraceExporterV2(config, clock, httpClient);
        }
        else {
            logger.info("Exporting to Dynatrace metrics API v1");
            this.exporter = new DynatraceExporterV1(config, clock, httpClient);
        }

        if (shouldAddZeroPercentile) {
            // zero percentiles automatically added should not be exported.
            this.config().meterFilter(zeroPercentileMeterFilter);
        }

        start(threadFactory);
    }

    public static Builder builder(DynatraceConfig config) {
        return new Builder(config);
    }

    @Override
    protected void publish() {
        exporter.export(getMeters());
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return this.exporter.getBaseTimeUnit();
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        if (useDynatraceSummaryInstruments) {
            return new DynatraceDistributionSummary(id, clock, distributionStatisticConfig, scale);
        }

        DistributionStatisticConfig config = distributionStatisticConfig;
        if (shouldAddZeroPercentile) {
            config = addZeroPercentileIfMissing(id, distributionStatisticConfig);
        }
        return super.newDistributionSummary(id, config, scale);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        if (useDynatraceSummaryInstruments) {
            return new DynatraceTimer(id, clock, distributionStatisticConfig, pauseDetector,
                    exporter.getBaseTimeUnit());
        }

        DistributionStatisticConfig config = distributionStatisticConfig;
        if (shouldAddZeroPercentile) {
            config = addZeroPercentileIfMissing(id, distributionStatisticConfig);
        }
        return super.newTimer(id, config, pauseDetector);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        if (useDynatraceSummaryInstruments) {
            return new DynatraceLongTaskTimer(id, clock, exporter.getBaseTimeUnit(), distributionStatisticConfig,
                    false);
        }

        DistributionStatisticConfig config = distributionStatisticConfig;
        if (shouldAddZeroPercentile) {
            config = addZeroPercentileIfMissing(id, distributionStatisticConfig);
        }
        return super.newLongTaskTimer(id, config);
    }

    private static class DenyZeroPercentileMeterFilter implements MeterFilter {

        private final Set<String> metersWithArtificialZeroPercentile = ConcurrentHashMap.newKeySet();

        private boolean hasArtificialZeroPercentile(Meter.Id id) {
            return metersWithArtificialZeroPercentile.contains(id.getName()) && "0".equals(id.getTag("phi"));
        }

        @Override
        public MeterFilterReply accept(Meter.Id id) {
            return hasArtificialZeroPercentile(id) ? DENY : NEUTRAL;
        }

        public void addMeterId(Meter.Id id) {
            metersWithArtificialZeroPercentile.add(id.getName() + ".percentile");
        }

    }

    private boolean containsZero(double[] percentiles) {
        return Arrays.stream(percentiles).anyMatch(percentile -> percentile == 0);
    }

    private DistributionStatisticConfig addZeroPercentileIfMissing(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig) {
        double[] percentiles;

        double[] configPercentiles = distributionStatisticConfig.getPercentiles();
        if (configPercentiles == null) {
            percentiles = new double[] { 0. };
            zeroPercentileMeterFilter.addMeterId(id);
        }
        else if (!containsZero(configPercentiles)) {
            percentiles = new double[configPercentiles.length + 1];
            System.arraycopy(configPercentiles, 0, percentiles, 0, configPercentiles.length);
            // theoretically this is already zero
            percentiles[configPercentiles.length] = 0;
            zeroPercentileMeterFilter.addMeterId(id);
        }
        else {
            // Zero percentile is explicitly added to the config, no need to add it to
            // drop list.
            return distributionStatisticConfig;
        }

        return DistributionStatisticConfig.builder()
            .percentiles(percentiles)
            .build()
            .merge(distributionStatisticConfig);
    }

    public static class Builder {

        private final DynatraceConfig config;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(DynatraceConfig config) {
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

        public DynatraceMeterRegistry build() {
            return new DynatraceMeterRegistry(config, clock, threadFactory, httpClient);
        }

    }

}
