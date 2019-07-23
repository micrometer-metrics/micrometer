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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.cumulative.*;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.step.*;
import io.micrometer.core.lang.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * A minimal meter registry implementation primarily used for tests.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class SimpleMeterRegistry extends MeterRegistry {
    private final SimpleConfig config;

    public SimpleMeterRegistry() {
        this(SimpleConfig.DEFAULT, Clock.SYSTEM);
    }

    public SimpleMeterRegistry(SimpleConfig config, Clock clock) {
        super(clock);
        this.config = config;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        DistributionStatisticConfig merged = distributionStatisticConfig.merge(DistributionStatisticConfig.builder()
                .expiry(config.step())
                .build());

        DistributionSummary summary;
        switch (config.mode()) {
            case CUMULATIVE:
                summary = new CumulativeDistributionSummary(id, clock, merged, scale, false);
                break;
            case STEP:
            default:
                summary = new StepDistributionSummary(id, clock, merged, scale, config.step().toMillis(), false);
                break;
        }

        HistogramGauges.registerWithCommonFormat(summary, this);

        return summary;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        DistributionStatisticConfig merged = distributionStatisticConfig.merge(DistributionStatisticConfig.builder()
                .expiry(config.step())
                .build());

        Timer timer;
        switch (config.mode()) {
            case CUMULATIVE:
                timer = new CumulativeTimer(id, clock, merged, pauseDetector, getBaseTimeUnit(), false);
                break;
            case STEP:
            default:
                timer = new StepTimer(id, clock, merged, pauseDetector, getBaseTimeUnit(), config.step().toMillis(), false);
                break;
        }

        HistogramGauges.registerWithCommonFormat(timer, this);

        return timer;
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        switch (config.mode()) {
            case CUMULATIVE:
                return new CumulativeCounter(id);
            case STEP:
            default:
                return new StepCounter(id, clock, config.step().toMillis());
        }
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return new DefaultLongTaskTimer(id, clock);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        switch (config.mode()) {
            case CUMULATIVE:
                return new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());

            case STEP:
            default:
                return new StepFunctionTimer<>(id, clock, config.step().toMillis(), obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
        }
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        switch (config.mode()) {
            case CUMULATIVE:
                return new CumulativeFunctionCounter<>(id, obj, countFunction);

            case STEP:
            default:
                return new StepFunctionCounter<>(id, clock, config.step().toMillis(), obj, countFunction);
        }
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

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        getMeters().stream()
                .sorted(Comparator.comparing(m -> m.getId().getName()))
                .forEach(m -> m.use(
                        gauge -> appendMeter(gauge, builder),
                        counter -> appendMeter(counter, builder),
                        timer -> appendMeter(timer, builder),
                        summary -> appendMeter(summary, builder),
                        longTaskTimer -> appendMeter(longTaskTimer, builder),
                        timeGauge -> appendMeter(timeGauge, builder),
                        functionCounter -> appendMeter(functionCounter, builder),
                        functionTimer -> appendMeter(functionTimer, builder),
                        meter -> appendMeter(meter, builder)
                ));

        return builder.toString();
    }

    private String getMeterName(Meter meter, String type) {

        final Id meterId = meter.getId();
        final List<Tag> tags = meterId.getTags();

        String meterName = "\n" + type + " " + meterId.getName();

        if (!tags.isEmpty()) {
            meterName += " {" + tags.stream()
                    .map(t -> t.getKey() + "=" + t.getValue())
                    .collect(Collectors.joining("; "))
                    + "}";
        }

        return meterName;
    }

    private String getMeterValueFormat(String separator, String conversion) {
        final int padding = 16 - separator.length();
        return "\n%1$" + padding + "s" + separator + "%2$" + conversion;
    }

    private void appendMeter(Gauge gauge, StringBuilder builder) {
        builder.append(String.format("%s value=%f", getMeterName(gauge, "Gauge"), gauge.value()));
    }

    private void appendMeter(Counter counter, StringBuilder builder) {
        builder.append(String.format("%s count=%f", getMeterName(counter, "Counter"), counter.count()));
    }

    private void appendMeter(Timer timer, StringBuilder builder) {
        appendHistogram(timer, "Timer", builder, getBaseTimeUnit());
    }

    private void appendMeter(DistributionSummary summary, StringBuilder builder) {
        appendHistogram(summary, "DistributionSummary", builder, null);
    }

    private void appendHistogram(HistogramSupport summary, String type, StringBuilder builder,
            @Nullable TimeUnit timeUnit) {
        final HistogramSnapshot snapshot = summary.takeSnapshot();

        final double total = timeUnit == null ? snapshot.total() : snapshot.total(timeUnit);
        final double mean = timeUnit == null ? snapshot.mean() : snapshot.mean(timeUnit);
        final double max = timeUnit == null ? snapshot.max() : snapshot.max(timeUnit);

        builder
                .append(getMeterName(summary, type))
                .append(String.format(getMeterValueFormat(" = ", "d"), "count", snapshot.count()))
                .append(String.format(getMeterValueFormat(" = ", "f"), "total", total))
                .append(String.format(getMeterValueFormat(" = ", "f"), "mean", mean))
                .append(String.format(getMeterValueFormat(" = ", "f"), "max", max))
        ;

        final ValueAtPercentile[] percentileValues = snapshot.percentileValues();
        if (percentileValues.length > 0) {
            builder.append("\n    percentileValues:");
            Arrays.stream(percentileValues)
                    .sorted(Comparator.comparingDouble(ValueAtPercentile::percentile))
                    .forEach(vap -> builder.append(String.format(
                            getMeterValueFormat(" <= ", "s"),
                            vap.percentile() * 100,
                            timeUnit == null ? vap.value() : vap.value(getBaseTimeUnit())
                    )));
        }

        final CountAtBucket[] histogramCounts = snapshot.histogramCounts();
        if (histogramCounts.length > 0) {
            builder.append("\n    histogramCounts:");
            Arrays.stream(histogramCounts)
                    .sorted(Comparator.comparingDouble(CountAtBucket::bucket))
                    .forEach(hc -> builder.append(String.format(
                            getMeterValueFormat(" <= ", "s"),
                            timeUnit == null ? hc.bucket() : hc.bucket(getBaseTimeUnit()),
                            hc.count()
                    )));
        }
    }

    private void appendMeter(LongTaskTimer timer, StringBuilder builder) {
        builder
                .append(getMeterName(timer, "Timer"))
                .append(String.format(getMeterValueFormat(" = ", "d"), "activeTasks", timer.activeTasks()))
                .append(String.format(getMeterValueFormat(" = ", "f"), "duration",
                        timer.duration(getBaseTimeUnit())));
    }

    private void appendMeter(TimeGauge gauge, StringBuilder builder) {
        builder.append(String.format("%s value=%f", getMeterName(gauge, "Gauge"),
                gauge.value(getBaseTimeUnit())));
    }


    private void appendMeter(FunctionCounter counter, StringBuilder builder) {
        builder.append(String.format("%s count=%f", getMeterName(counter, "Counter"), counter.count()));
    }

    private void appendMeter(FunctionTimer timer, StringBuilder builder) {
        builder
                .append(getMeterName(timer, "Timer"))
                .append(String.format(getMeterValueFormat(" = ", "f"), "count", timer.count()))
                .append(String.format(getMeterValueFormat(" = ", "f"),
                        "totalTime", timer.totalTime(getBaseTimeUnit())))
                .append(String.format(getMeterValueFormat(" = ", "f"), "mean",
                        timer.mean(getBaseTimeUnit())));
    }

    private void appendMeter(Meter meter, StringBuilder builder) {
        builder.append(getMeterName(meter, "Gauge"));

        meter.measure().forEach(measurement -> builder.append(
                String.format(getMeterValueFormat(" = ", "f"),
                        measurement.getStatistic(), measurement.getValue())));
    }
}
