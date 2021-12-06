/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
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

    private final AbstractDynatraceExporter exporter;

    @SuppressWarnings("deprecation")
    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private DynatraceMeterRegistry(DynatraceConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);

        if (config.apiVersion() == DynatraceApiVersion.V2) {
            logger.info("Exporting to Dynatrace metrics API v2");
            this.exporter = new DynatraceExporterV2(config, clock, httpClient);
            registerMinPercentile();
        } else {
            logger.info("Exporting to Dynatrace metrics API v1");
            this.exporter = new DynatraceExporterV1(config, clock, httpClient);
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

    /**
     * As the micrometer summary statistics (DistributionSummary, and a number of timer meter types)
     * do not provide the minimum values that are required by Dynatrace to ingest summary metrics,
     * we add the 0th percentile to each summary statistic and use that as the minimum value.
     */
    private void registerMinPercentile() {
        config().meterFilter(new MeterFilter() {
            private final Set<String> metersWithArtificialZeroPercentile = ConcurrentHashMap.newKeySet();

            /**
             * Adds 0th percentile if the user hasn't already added
             * and tracks those meter names where the 0th percentile was artificially added.
             */
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                double[] percentiles;

                if (config.getPercentiles() == null) {
                    percentiles = new double[] {0};
                    metersWithArtificialZeroPercentile.add(id.getName() + ".percentile");
                } else if (!containsZeroPercentile(config)) {
                    percentiles = new double[config.getPercentiles().length + 1];
                    System.arraycopy(config.getPercentiles(), 0, percentiles, 0, config.getPercentiles().length);
                    percentiles[config.getPercentiles().length] = 0; // theoretically this is already zero
                    metersWithArtificialZeroPercentile.add(id.getName() + ".percentile");
                } else {
                    percentiles = config.getPercentiles();
                }

                return DistributionStatisticConfig.builder()
                        .percentiles(percentiles)
                        .build()
                        .merge(config);
            }

            /**
             * Denies artificially added 0th percentile meters.
             */
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                return hasArtificialZerothPercentile(id) ? DENY : NEUTRAL;
            }

            private boolean containsZeroPercentile(DistributionStatisticConfig config) {
                return Arrays.stream(config.getPercentiles()).anyMatch(percentile -> percentile == 0);
            }

            private boolean hasArtificialZerothPercentile(Meter.Id id) {
                return metersWithArtificialZeroPercentile.contains(id.getName()) && "0".equals(id.getTag("phi"));
            }
        });
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
