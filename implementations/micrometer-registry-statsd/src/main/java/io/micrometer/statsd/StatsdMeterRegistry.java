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
package io.micrometer.statsd;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.statsd.internal.*;
import io.micrometer.statsd.internal.flux.FluxMeterProcessor;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.stream.DoubleStream;

/**
 * {@link MeterRegistry} for StatsD.
 * <p>
 * The following StatsD line protocols are supported:
 *
 * <ul>
 * <li>Datadog (default)</li>
 * <li>Etsy</li>
 * <li>Telegraf</li>
 * <li>Sysdig</li>
 * </ul>
 * <p>
 * See {@link StatsdFlavor} for more details.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Tommy Ludwig
 * @since 1.0.0
 */
public class StatsdMeterRegistry extends MeterRegistry {

    private static final WarnThenDebugLogger warnThenDebugLogger = new WarnThenDebugLogger(StatsdMeterRegistry.class);

    private final StatsdConfig statsdConfig;

    private final HierarchicalNameMapper nameMapper;

    private final Map<Meter.Id, StatsdPollable> pollableMeters = new ConcurrentHashMap<>();

    private final AtomicBoolean started = new AtomicBoolean();

    @Nullable
    private BiFunction<Meter.Id, DistributionStatisticConfig, StatsdLineBuilder> lineBuilderFunction;

    final MeterProcessor processor;

    public StatsdMeterRegistry(StatsdConfig config, Clock clock) {
        this(config, HierarchicalNameMapper.DEFAULT, clock);
    }

    /**
     * Use this constructor for Etsy-flavored StatsD when you need to influence the way
     * Micrometer's dimensional {@link io.micrometer.core.instrument.Meter.Id Meter.Id} is
     * written to a flat hierarchical name.
     * @param config The StatsD configuration.
     * @param nameMapper A strategy for flattening dimensional IDs.
     * @param clock The clock to use for timing and polling certain types of meters.
     */
    public StatsdMeterRegistry(StatsdConfig config, HierarchicalNameMapper nameMapper, Clock clock) {
        this(config, nameMapper, namingConventionFromFlavor(config.flavor()), clock, null, null);
    }

    private StatsdMeterRegistry(StatsdConfig config, HierarchicalNameMapper nameMapper,
            NamingConvention namingConvention, Clock clock,
            @Nullable BiFunction<Meter.Id, DistributionStatisticConfig, StatsdLineBuilder> lineBuilderFunction,
            @Nullable MeterProcessor processor) {
        super(clock);

        config.requireValid();

        this.statsdConfig = config;
        this.nameMapper = nameMapper;
        this.lineBuilderFunction = lineBuilderFunction;

        if (processor == null) {
            processor = new FluxMeterProcessor(config);
        }
        this.processor = processor;

        config().namingConvention(namingConvention);

        config().onMeterRemoved(meter -> meter.use(this::removePollableMeter, c -> ((StatsdCounter) c).shutdown(),
                t -> ((StatsdTimer) t).shutdown(), d -> ((StatsdDistributionSummary) d).shutdown(),
                this::removePollableMeter, this::removePollableMeter, this::removePollableMeter,
                this::removePollableMeter, m -> {
                    for (Measurement measurement : m.measure()) {
                        pollableMeters.remove(m.getId().withTag(measurement.getStatistic()));
                    }
                }));

        if (config.enabled()) {
            start();
        }
    }

    public static Builder builder(StatsdConfig config) {
        return new Builder(config);
    }

    private static NamingConvention namingConventionFromFlavor(StatsdFlavor flavor) {
        switch (flavor) {
            case DATADOG:
            case SYSDIG:
                return NamingConvention.dot;
            case TELEGRAF:
                return NamingConvention.snakeCase;
            default:
                return NamingConvention.camelCase;
        }
    }

    private <M extends Meter> void removePollableMeter(M m) {
        pollableMeters.remove(m.getId());
    }

    void poll() {
        for (Map.Entry<Meter.Id, StatsdPollable> pollableMeter : pollableMeters.entrySet()) {
            try {
                pollableMeter.getValue().poll();
            }
            catch (RuntimeException e) {
                warnThenDebugLogger.log("Failed to poll a meter '" + pollableMeter.getKey().getName() + "'.", e);
            }
        }
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            processor.start(() -> {
                poll();
            });
        }
    }

    public void stop() {
        if (started.compareAndSet(true, false)) {
            processor.stop();
        }
    }

    @Override
    public void close() {
        poll();
        stop();
        super.close();
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        StatsdGauge<T> gauge = new StatsdGauge<>(id, lineBuilder(id), (line) -> this.processor.next(line), obj,
                valueFunction, statsdConfig.publishUnchangedMeters());
        pollableMeters.put(id, gauge);
        return gauge;
    }

    private StatsdLineBuilder lineBuilder(Meter.Id id) {
        return lineBuilder(id, null);
    }

    private StatsdLineBuilder lineBuilder(Meter.Id id,
            @Nullable DistributionStatisticConfig distributionStatisticConfig) {
        if (lineBuilderFunction == null) {
            lineBuilderFunction = (id2, dsc2) -> {
                switch (statsdConfig.flavor()) {
                    case DATADOG:
                        return new DatadogStatsdLineBuilder(id2, config(), dsc2);
                    case TELEGRAF:
                        return new TelegrafStatsdLineBuilder(id2, config());
                    case SYSDIG:
                        return new SysdigStatsdLineBuilder(id2, config());
                    case ETSY:
                    default:
                        return new EtsyStatsdLineBuilder(id2, config(), nameMapper);
                }
            };
        }
        return lineBuilderFunction.apply(id, distributionStatisticConfig);
    }

    private DistributionStatisticConfig addInfBucket(DistributionStatisticConfig config) {
        double[] serviceLevelObjectives = config.getServiceLevelObjectiveBoundaries() == null
                ? new double[] { Double.POSITIVE_INFINITY }
                : DoubleStream.concat(Arrays.stream(config.getServiceLevelObjectiveBoundaries()),
                        DoubleStream.of(Double.POSITIVE_INFINITY)).toArray();
        return DistributionStatisticConfig.builder().serviceLevelObjectives(serviceLevelObjectives).build()
                .merge(config);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new StatsdCounter(id, lineBuilder(id), (line) -> this.processor.next(line));
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        StatsdLongTaskTimer ltt = new StatsdLongTaskTimer(id, lineBuilder(id, distributionStatisticConfig),
                (line) -> this.processor.next(line), clock, statsdConfig.publishUnchangedMeters(),
                distributionStatisticConfig, getBaseTimeUnit());
        HistogramGauges.registerWithCommonFormat(ltt, this);
        pollableMeters.put(id, ltt);
        return ltt;
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {

        // Adds an infinity bucket for SLO violation calculation
        if (distributionStatisticConfig.getServiceLevelObjectiveBoundaries() != null) {
            distributionStatisticConfig = addInfBucket(distributionStatisticConfig);
        }

        Timer timer = new StatsdTimer(id, lineBuilder(id, distributionStatisticConfig),
                (line) -> this.processor.next(line), clock, distributionStatisticConfig, pauseDetector,
                getBaseTimeUnit(), statsdConfig.step().toMillis());
        HistogramGauges.registerWithCommonFormat(timer, this);
        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {

        // Adds an infinity bucket for SLO violation calculation
        if (distributionStatisticConfig.getServiceLevelObjectiveBoundaries() != null) {
            distributionStatisticConfig = addInfBucket(distributionStatisticConfig);
        }

        DistributionSummary summary = new StatsdDistributionSummary(id, lineBuilder(id, distributionStatisticConfig),
                (line) -> this.processor.next(line), clock, distributionStatisticConfig, scale);
        HistogramGauges.registerWithCommonFormat(summary, this);
        return summary;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        StatsdFunctionCounter<T> fc = new StatsdFunctionCounter<>(id, obj, countFunction, lineBuilder(id),
                (line) -> this.processor.next(line));
        pollableMeters.put(id, fc);
        return fc;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        StatsdFunctionTimer<T> ft = new StatsdFunctionTimer<>(id, obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnit, getBaseTimeUnit(), lineBuilder(id), (line) -> this.processor.next(line));
        pollableMeters.put(id, ft);
        return ft;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        measurements.forEach(ms -> {
            StatsdLineBuilder line = lineBuilder(id);
            Statistic stat = ms.getStatistic();
            switch (stat) {
                case COUNT:
                case TOTAL:
                case TOTAL_TIME:
                    pollableMeters.put(id.withTag(stat),
                            () -> this.processor.next(line.count((long) ms.getValue(), stat)));
                    break;
                case VALUE:
                case ACTIVE_TASKS:
                case DURATION:
                case UNKNOWN:
                    pollableMeters.put(id.withTag(stat), () -> this.processor.next(line.gauge(ms.getValue(), stat)));
                    break;
            }
        });
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder().expiry(statsdConfig.step()).build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    /**
     * @return constant {@literal -1}
     * @deprecated queue size is no longer available since 1.4.0
     */
    @Deprecated
    public int queueSize() {
        return -1;
    }

    /**
     * @return constant {@literal -1}
     * @deprecated queue capacity is no longer available since 1.4.0
     */
    @Deprecated
    public int queueCapacity() {
        return -1;
    }

    /**
     * A builder for configuration of less common knobs on {@link StatsdMeterRegistry}.
     */
    @Incubating(since = "1.0.1")
    public static class Builder {

        private final StatsdConfig config;

        private Clock clock = Clock.SYSTEM;

        private NamingConvention namingConvention;

        private HierarchicalNameMapper nameMapper = HierarchicalNameMapper.DEFAULT;

        @Nullable
        private BiFunction<Meter.Id, DistributionStatisticConfig, StatsdLineBuilder> lineBuilderFunction = null;

        @Nullable
        private Consumer<String> lineSink;

        @Nullable
        private MeterProcessor processor;

        Builder(StatsdConfig config) {
            this.config = config;
            this.namingConvention = namingConventionFromFlavor(config.flavor());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Used for completely customizing the StatsD line format. Intended for use by
         * custom, proprietary StatsD flavors.
         * @param lineBuilderFunction A mapping from a meter ID and a Distribution
         * statistic configuration to a StatsD line generator that knows how to write
         * counts, gauges timers, and histograms in the proprietary format.
         * @return This builder.
         * @since 1.8.0
         */
        public Builder lineBuilder(
                BiFunction<Meter.Id, DistributionStatisticConfig, StatsdLineBuilder> lineBuilderFunction) {
            this.lineBuilderFunction = lineBuilderFunction;
            return this;
        }

        /**
         * Used for completely customizing the StatsD line format. Intended for use by
         * custom, proprietary StatsD flavors.
         * @param lineBuilderFunction A mapping from a meter ID to a StatsD line generator
         * that knows how to write counts, gauges timers, and histograms in the
         * proprietary format.
         * @return This builder.
         * @deprecated Use {@link #lineBuilder(BiFunction)} instead since 1.8.0.
         */
        @Deprecated
        public Builder lineBuilder(Function<Meter.Id, StatsdLineBuilder> lineBuilderFunction) {
            this.lineBuilderFunction = (id, dsc) -> lineBuilderFunction.apply(id);
            return this;
        }

        public Builder nameMapper(HierarchicalNameMapper nameMapper) {
            this.nameMapper = nameMapper;
            return this;
        }

        /**
         * @deprecated Use {@link #processor(Processor)} instead since 1.10.0.
         */
        @Deprecated
        public Builder lineSink(Consumer<String> lineSink) {
            this.lineSink = lineSink;
            return this;
        }

        public Builder processor(MeterProcessor processor) {
            this.processor = processor;
            return this;
        }

        public StatsdMeterRegistry build() {
            if (processor == null) {
                this.processor = new FluxMeterProcessor(config, this.lineSink, null);
            }
            return new StatsdMeterRegistry(config, nameMapper, namingConvention, clock, lineBuilderFunction, processor);
        }

    }

}
