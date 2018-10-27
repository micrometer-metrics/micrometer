package io.micrometer.core.instrument.logging;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepDistributionSummary;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepTimer;
import io.micrometer.core.instrument.util.TimeUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import static io.micrometer.core.instrument.util.DoubleFormat.decimalOrWhole;
import static java.util.stream.Collectors.joining;

/**
 * @author Jon Schneider
 */
@Incubating(since = "1.1.0")
public class LoggingMeterRegistry extends StepMeterRegistry {
    private final LoggingRegistryConfig config;
    private final Consumer<String> loggingSink;

    public LoggingMeterRegistry() {
        this(LoggingRegistryConfig.DEFAULT, Clock.SYSTEM);
    }

    public LoggingMeterRegistry(LoggingRegistryConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory(), defaultLoggingSink());
    }

    private LoggingMeterRegistry(LoggingRegistryConfig config, Clock clock, ThreadFactory threadFactory, Consumer<String> loggingSink) {
        super(config, clock);
        this.config = config;
        this.loggingSink = loggingSink;
        config().namingConvention(NamingConvention.dot);
        start(threadFactory);
    }

    private static Consumer<String> defaultLoggingSink() {
        try {
            Class<?> slf4jLogger = Class.forName("org.slf4j.LoggerFactory");
            Object logger = slf4jLogger.getMethod("getLogger", Class.class).invoke(null, LoggingMeterRegistry.class);
            final Method loggerInfo = logger.getClass().getMethod("info", String.class);
            return stmt -> {
                try {
                    loggerInfo.invoke(logger, stmt);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e); // should never happen
                }
            };
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return System.out::println;
        }
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            loggingSink.accept("publishing metrics to logs every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        if (config.enabled()) {
            getMeters().stream()
                    .sorted((m1, m2) -> {
                        int typeComp = m1.getId().getType().compareTo(m2.getId().getType());
                        if (typeComp == 0) {
                            return m1.getId().getName().compareTo(m2.getId().getName());
                        }
                        return typeComp;
                    })
                    .forEach(m -> {
                        Printer print = new Printer(m);
                        m.use(
                                gauge -> loggingSink.accept(print.id() + " value=" + print.value(gauge.value())),
                                counter -> {
                                    if (counter.count() == 0) return;
                                    loggingSink.accept(print.id() + " throughput=" + print.rate(counter.count()));
                                },
                                timer -> {
                                    HistogramSnapshot snapshot = timer.takeSnapshot();
                                    if (snapshot.count() == 0) return;
                                    loggingSink.accept(print.id() + " throughput=" + print.rate(snapshot.count()) +
                                            " mean=" + print.time(snapshot.mean(getBaseTimeUnit())) +
                                            " max=" + print.time(snapshot.max(getBaseTimeUnit())));
                                },
                                summary -> {
                                    HistogramSnapshot snapshot = summary.takeSnapshot();
                                    if (snapshot.count() == 0) return;
                                    loggingSink.accept(print.id() + " throughput=" + print.rate(snapshot.count()) +
                                            " mean=" + print.value(snapshot.mean()) +
                                            " max=" + print.value(snapshot.max()));
                                },
                                longTaskTimer -> {
                                    if (longTaskTimer.activeTasks() == 0) return;
                                    loggingSink.accept(print.id() +
                                            " active=" + print.value(longTaskTimer.activeTasks()) +
                                            " duration=" + print.time(longTaskTimer.duration(getBaseTimeUnit())));
                                },
                                timeGauge -> {
                                    if (timeGauge.value(getBaseTimeUnit()) == 0) return;
                                    loggingSink.accept(print.id() + " value=" + print.time(timeGauge.value(getBaseTimeUnit())));
                                },
                                counter -> {
                                    if (counter.count() == 0) return;
                                    loggingSink.accept(print.id() + " throughput=" + print.rate(counter.count()));
                                },
                                timer -> {
                                    if (timer.count() == 0) return;
                                    loggingSink.accept(print.id() + " throughput=" + print.rate(timer.count()) +
                                            " mean=" + print.time(timer.mean(getBaseTimeUnit())));
                                },
                                meter -> loggingSink.accept(print.id() + StreamSupport.stream(meter.measure().spliterator(), false)
                                        .map(ms -> ms.getStatistic().getTagValueRepresentation() + "=" + decimalOrWhole(ms.getValue())))
                        );
                    });
        }
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        return new StepTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                this.config.step().toMillis(), false);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new StepDistributionSummary(id, clock, distributionStatisticConfig, scale,
                config.step().toMillis(), false);
    }

    private class Printer {
        private final Meter meter;
        private final String baseUnit;

        private Printer(Meter meter) {
            this.meter = meter;
            String unit = meter.getId().getBaseUnit();
            this.baseUnit = unit == null ? "" : " " + unit;
        }

        String id() {
            return getConventionName(meter.getId()) + getConventionTags(meter.getId()).stream()
                    .map(t -> t.getKey() + "=" + t.getValue())
                    .collect(joining(",", "{", "}"));
        }

        String time(double time) {
            return TimeUtils.format(Duration.ofNanos((long) TimeUtils.convert(time, getBaseTimeUnit(), TimeUnit.NANOSECONDS)));
        }

        String rate(double rate) {
            return humanReadableBaseUnit(rate / (double) config.step().getSeconds()) + "/s";
        }

        String value(double value) {
            return humanReadableBaseUnit(value);
        }

        // see https://stackoverflow.com/a/3758880/510017
        private String humanReadableByteCount(double bytes) {
            int unit = 1024;
            if (bytes < unit) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(unit));
            String pre = "KMGTPE" .charAt(exp - 1) + "i";
            return String.format("%.2f %sB", bytes / Math.pow(unit, exp), pre);
        }

        private String humanReadableBaseUnit(double value) {
            if (" bytes" .equals(baseUnit)) {
                return humanReadableByteCount(value);
            }
            return decimalOrWhole(value) + baseUnit;
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
        private ThreadFactory threadFactory = Executors.defaultThreadFactory();
        private Consumer<String> loggingSink = defaultLoggingSink();

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

        public LoggingMeterRegistry build() {
            return new LoggingMeterRegistry(config, clock, threadFactory, loggingSink);
        }
    }
}