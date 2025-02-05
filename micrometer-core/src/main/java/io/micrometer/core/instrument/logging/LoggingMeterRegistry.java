/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.core.instrument.logging;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepDistributionSummary;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepTimer;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;

import java.time.Duration;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static io.micrometer.core.instrument.util.DoubleFormat.decimalOrNan;
import static io.micrometer.core.instrument.util.DoubleFormat.wholeOrDecimal;
import static java.util.stream.Collectors.joining;

/**
 * Logging {@link io.micrometer.core.instrument.MeterRegistry}.
 *
 * @author Jon Schneider
 * @author Matthieu Borgraeve
 * @author Francois Staudt
 * @since 1.1.0
 */
@Incubating(since = "1.1.0")
public class LoggingMeterRegistry extends StepMeterRegistry {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(LoggingMeterRegistry.class);

    private final LoggingRegistryConfig config;

    private final Consumer<String> loggingSink;

    private final Function<Meter, String> meterIdPrinter;

    public LoggingMeterRegistry() {
        this(LoggingRegistryConfig.DEFAULT, Clock.SYSTEM);
    }

    /**
     * Constructor allowing a custom clock and configuration.
     * @param config the LoggingRegistryConfig
     * @param clock the Clock
     */
    public LoggingMeterRegistry(LoggingRegistryConfig config, Clock clock) {
        this(config, clock, log::info);
    }

    /**
     * Constructor allowing custom sink instead of a default {@code log::info}.
     * @param loggingSink the custom sink that will be called for each time series.
     * @since 1.11.0
     */
    public LoggingMeterRegistry(Consumer<String> loggingSink) {
        this(LoggingRegistryConfig.DEFAULT, Clock.SYSTEM, loggingSink);
    }

    /**
     * Constructor allowing a custom sink, clock and configuration.
     * @param config the LoggingRegistryConfig
     * @param clock the Clock
     * @param loggingSink the custom sink that will be called for each time series.
     * @since 1.11.0
     */
    public LoggingMeterRegistry(LoggingRegistryConfig config, Clock clock, Consumer<String> loggingSink) {
        this(config, clock, new NamedThreadFactory("logging-metrics-publisher"), loggingSink, null);
    }

    private LoggingMeterRegistry(LoggingRegistryConfig config, Clock clock, ThreadFactory threadFactory,
            Consumer<String> loggingSink, @Nullable Function<Meter, String> meterIdPrinter) {
        super(config, clock);
        this.config = config;
        this.loggingSink = loggingSink;
        this.meterIdPrinter = meterIdPrinter != null ? meterIdPrinter : defaultMeterIdPrinter();
        config().namingConvention(NamingConvention.dot);
        start(threadFactory);
    }

    private Function<Meter, String> defaultMeterIdPrinter() {
        return (meter) -> getConventionName(meter.getId()) + getConventionTags(meter.getId()).stream()
            .map(t -> t.getKey() + "=" + t.getValue())
            .collect(joining(",", "{", "}"));
    }

    @Override
    protected void publish() {
        if (config.enabled()) {
            getMeters().stream().sorted((m1, m2) -> {
                int typeComp = m1.getId().getType().compareTo(m2.getId().getType());
                if (typeComp == 0) {
                    return m1.getId().getName().compareTo(m2.getId().getName());
                }
                return typeComp;
            }).forEach(m -> {
                Printer print = new Printer(m);
                m.use(gauge -> loggingSink.accept(print.id() + " value=" + print.value(gauge.value())), counter -> {
                    double count = counter.count();
                    if (!config.logInactive() && count == 0)
                        return;
                    loggingSink.accept(print.id() + " delta_count=" + print.humanReadableBaseUnit(count)
                            + " throughput=" + print.rate(count));
                }, timer -> {
                    HistogramSnapshot snapshot = timer.takeSnapshot();
                    long count = snapshot.count();
                    if (!config.logInactive() && count == 0)
                        return;
                    loggingSink.accept(print.id() + " delta_count=" + wholeOrDecimal(count) + " throughput="
                            + print.unitlessRate(count) + " mean=" + print.time(snapshot.mean(getBaseTimeUnit()))
                            + " max=" + print.time(snapshot.max(getBaseTimeUnit())));
                }, summary -> {
                    HistogramSnapshot snapshot = summary.takeSnapshot();
                    long count = snapshot.count();
                    if (!config.logInactive() && count == 0)
                        return;
                    loggingSink.accept(print.id() + " delta_count=" + wholeOrDecimal(count) + " throughput="
                            + print.unitlessRate(count) + " mean=" + print.value(snapshot.mean()) + " max="
                            + print.value(snapshot.max()));
                }, longTaskTimer -> {
                    int activeTasks = longTaskTimer.activeTasks();
                    if (!config.logInactive() && activeTasks == 0)
                        return;
                    HistogramSnapshot snapshot = longTaskTimer.takeSnapshot();
                    loggingSink.accept(print.id() + " active=" + activeTasks + " duration="
                            + print.time(longTaskTimer.duration(getBaseTimeUnit())) + " mean="
                            + print.time(snapshot.mean(getBaseTimeUnit())) + " max="
                            + print.time(snapshot.max(getBaseTimeUnit())));
                }, timeGauge -> {
                    double value = timeGauge.value(getBaseTimeUnit());
                    if (!config.logInactive() && value == 0)
                        return;
                    loggingSink.accept(print.id() + " value=" + print.time(value));
                }, functionCounter -> {
                    double count = functionCounter.count();
                    if (!config.logInactive() && count == 0)
                        return;
                    loggingSink.accept(print.id() + " delta_count=" + print.humanReadableBaseUnit(count)
                            + " throughput=" + print.rate(count));
                }, functionTimer -> {
                    double count = functionTimer.count();
                    if (!config.logInactive() && count == 0)
                        return;
                    loggingSink.accept(print.id() + " delta_count=" + wholeOrDecimal(count) + " throughput="
                            + print.unitlessRate(count) + " mean=" + print.time(functionTimer.mean(getBaseTimeUnit())));
                }, meter -> loggingSink.accept(writeMeter(meter, print)));
            });
        }
    }

    String writeMeter(Meter meter, Printer print) {
        return StreamSupport.stream(meter.measure().spliterator(), false).map(ms -> {
            String msLine = ms.getStatistic().getTagValueRepresentation() + "=";
            switch (ms.getStatistic()) {
                case TOTAL:
                case MAX:
                case VALUE:
                    return msLine + print.value(ms.getValue());
                case TOTAL_TIME:
                case DURATION:
                    return msLine + print.time(ms.getValue());
                case COUNT:
                    return "delta_count=" + print.humanReadableBaseUnit(ms.getValue()) + ", throughput="
                            + print.rate(ms.getValue());
                default:
                    return msLine + decimalOrNan(ms.getValue());
            }
        }).collect(joining(", ", print.id() + " ", ""));
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        return new StepTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                this.config.step().toMillis(), false);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new StepDistributionSummary(id, clock, distributionStatisticConfig, scale, config.step().toMillis(),
                false);
    }

    class Printer {

        private final Meter meter;

        Printer(Meter meter) {
            this.meter = meter;
        }

        String id() {
            return meterIdPrinter.apply(meter);
        }

        String time(double time) {
            return TimeUtils
                .format(Duration.ofNanos((long) TimeUtils.convert(time, getBaseTimeUnit(), TimeUnit.NANOSECONDS)));
        }

        String rate(double value) {
            return humanReadableBaseUnit(value / (double) config.step().getSeconds()) + "/s";
        }

        String unitlessRate(double value) {
            return decimalOrNan(value / (double) config.step().getSeconds()) + "/s";
        }

        String value(double value) {
            return humanReadableBaseUnit(value);
        }

        // see https://stackoverflow.com/a/3758880/510017
        String humanReadableByteCount(double bytes) {
            int unit = 1024;
            if (bytes < unit || Double.isNaN(bytes))
                return decimalOrNan(bytes) + " B";
            int exp = (int) (Math.log(bytes) / Math.log(unit));
            String pre = "KMGTPE".charAt(exp - 1) + "i";
            return decimalOrNan(bytes / Math.pow(unit, exp)) + " " + pre + "B";
        }

        String humanReadableBaseUnit(double value) {
            String baseUnit = meter.getId().getBaseUnit();
            if (BaseUnits.BYTES.equals(baseUnit)) {
                return humanReadableByteCount(value);
            }
            return decimalOrNan(value) + (baseUnit != null ? " " + baseUnit : "");
        }

    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static Builder builder(LoggingRegistryConfig config) {
        return new Builder(config);
    }

    public static class Builder {

        private final LoggingRegistryConfig config;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = new NamedThreadFactory("logging-metrics-publisher");

        private Consumer<String> loggingSink = log::info;

        @Nullable
        private Function<Meter, String> meterIdPrinter;

        Builder(LoggingRegistryConfig config) {
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

        public Builder loggingSink(Consumer<String> loggingSink) {
            this.loggingSink = loggingSink;
            return this;
        }

        /**
         * Configure printer for meter IDs.
         * @param meterIdPrinter printer to use for meter IDs
         * @return this builder instance
         * @since 1.2.0
         */
        public Builder meterIdPrinter(Function<Meter, String> meterIdPrinter) {
            this.meterIdPrinter = meterIdPrinter;
            return this;
        }

        public LoggingMeterRegistry build() {
            return new LoggingMeterRegistry(config, clock, threadFactory, loggingSink, meterIdPrinter);
        }

    }

}
