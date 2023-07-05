/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.prometheusnative;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.core.metrics.*;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import static io.prometheus.metrics.model.snapshots.Unit.nanosToSeconds;

/**
 * Meter registry for the upcoming Prometheus Java client library version 1.0.
 */
public class PrometheusMeterRegistry extends MeterRegistry {

    private final PrometheusProperties prometheusProperties;

    private final PrometheusRegistry prometheusRegistry;

    // The following maps have Meter.Id.getName() as their key.

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    private final Map<String, CounterWithCallback> countersWithCallback = new ConcurrentHashMap<>();

    private final Map<String, GaugeWithCallback> gauges = new ConcurrentHashMap<>();

    private final Map<String, Histogram> histograms = new ConcurrentHashMap<>();

    private final Map<String, Summary> summaries = new ConcurrentHashMap<>();

    private final Map<String, SummaryWithCallback> summariesWithCallback = new ConcurrentHashMap<>();

    private final Map<String, Info> infos = new ConcurrentHashMap<>();

    private final Map<String, Map<Meter.Id, CounterCallback<?>>> counterCallbackMap = new ConcurrentHashMap<>();

    private final Map<String, Map<Meter.Id, GaugeCallback<?>>> gaugeCallbackMap = new ConcurrentHashMap<>();

    private final Map<String, Map<Meter.Id, SummaryCallback<?>>> summaryCallbackMap = new ConcurrentHashMap<>();

    public PrometheusMeterRegistry(PrometheusConfig config, PrometheusRegistry prometheusRegistry, Clock clock) {
        super(clock);
        this.prometheusRegistry = prometheusRegistry;
        this.prometheusProperties = config.getPrometheusProperties();
    }

    @Override
    protected PrometheusCounter newCounter(Meter.Id id) {
        Counter counter = getOrCreateCounter(id);
        return new PrometheusCounter(id, counter, counter.withLabelValues(getLabelValues(id.getTags())));
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
        if ("jvm.info".equals(id.getName())) {
            // Micrometer does not have info metrics, so info metrics are represented as
            // Gauge.
            // This is a hack to represent JvmInfoMetrics as an Info rather than a Gauge.
            return newInfo(id);
        }
        else {
            return newGaugeWithCallback(id, obj, valueFunction);
        }
    }

    private <T> PrometheusGaugeWithCallback newGaugeWithCallback(Meter.Id id, T obj,
            ToDoubleFunction<T> valueFunction) {
        GaugeWithCallback gauge = getOrCreateGaugeWithCallback(id, obj, valueFunction);
        return new PrometheusGaugeWithCallback(id, gauge);
    }

    private PrometheusInfoGauge newInfo(Meter.Id id) {
        id = id.withBaseUnit(null);
        Info info = getOrCreateInfo(id);
        info.infoLabelValues(getLabelValues(id.getTags()));
        return new PrometheusInfoGauge(id);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        Max max = newMax(id, distributionStatisticConfig);
        if (distributionStatisticConfig.isPublishingHistogram()) {
            Histogram histogram = getOrCreateHistogram(id, distributionStatisticConfig);
            return new PrometheusHistogram(id, max, histogram, histogram.withLabelValues(getLabelValues(id.getTags())));
        }
        else {
            Summary summary = getOrCreateSummary(id, distributionStatisticConfig);
            return new PrometheusSummary(id, max, summary, summary.withLabelValues(getLabelValues(id.getTags())));
        }
    }

    /**
     * In Prometheus _max is not part of Histograms and Summaries. Therefore, newMax
     * registers a Prometheus Gauge to track the max value.
     */
    private Max newMax(Meter.Id id, DistributionStatisticConfig config) {
        Max max = new Max(config);
        getOrCreateGaugeWithCallback(id.withName(id.getName() + ".max"), max, Max::get);
        return max;
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        Max max = newMax(id, distributionStatisticConfig);
        if (distributionStatisticConfig.isPublishingHistogram()) {
            Histogram histogram = getOrCreateHistogram(id, distributionStatisticConfig);
            return new PrometheusTimer<Histogram, HistogramSnapshot.HistogramDataPointSnapshot>(id, max, histogram,
                    histogram.withLabelValues(getLabelValues(id.getTags())));
        }
        else {
            Summary summary = getOrCreateSummary(id, distributionStatisticConfig);
            return new PrometheusTimer<Summary, SummarySnapshot.SummaryDataPointSnapshot>(id, max, summary,
                    summary.withLabelValues(getLabelValues(id.getTags())));
        }
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        SummaryWithCallback summary = getOrCreateSummaryWithCallback(id, obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnit);
        return new PrometheusFunctionTimer(id, summary);
    }

    /**
     * Example: This is used to create {@code http.server.requests.active}.
     */
    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        Max max = newMax(id, distributionStatisticConfig);
        if (distributionStatisticConfig.isPublishingHistogram()) {
            Histogram histogram = getOrCreateHistogram(id, distributionStatisticConfig);
            return new PrometheusLongTaskTimer<>(id, max, histogram,
                    histogram.withLabelValues(getLabelValues(id.getTags())));
        }
        else {
            Summary summary = getOrCreateSummary(id, distributionStatisticConfig);
            return new PrometheusLongTaskTimer<>(id, max, summary,
                    summary.withLabelValues(getLabelValues(id.getTags())));
        }
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        CounterWithCallback counter = getOrCreateCounterWithCallback(id, obj, countFunction);
        return new PrometheusCounterWithCallback(id, counter);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder().build().merge(DistributionStatisticConfig.DEFAULT);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        // TODO: This is not the correct implementation, the original
        // PrometheusMeterRegistry does something different here.
        switch (type) {
            case COUNTER:
                getOrCreateCounterWithCallback(id, measurements, m -> {
                    for (Measurement measurement : m) {
                        if (measurement.getStatistic() == Statistic.TOTAL
                                || measurement.getStatistic() == Statistic.TOTAL_TIME) {
                            return measurement.getValue();
                        }
                    }
                    return Double.NaN;
                });
                break;
            case TIMER:
                getOrCreateSummaryWithCallback(id, measurements, m -> {
                    // count
                    for (Measurement measurement : measurements) {
                        if (measurement.getStatistic() == Statistic.COUNT) {
                            return (long) measurement.getValue();
                        }
                    }
                    return 0L;
                }, m -> {
                    // sum
                    for (Measurement measurement : measurements) {
                        if (measurement.getStatistic() == Statistic.TOTAL
                                || measurement.getStatistic() == Statistic.TOTAL_TIME) {
                            return measurement.getValue();
                        }
                    }
                    return Double.NaN;
                }, TimeUnit.SECONDS); // seconds is our base time unit, so SECONDS means
                                      // don't convert the sum.
                break;
            // TODO: GAUGE, LONG_TASK_TIMER, TIMER, OTHER
            default:
                throw new UnsupportedOperationException("Meter type " + type + " not implemented yet.");
        }
        return new DefaultMeter(id, type, measurements);
    }

    private Counter getOrCreateCounter(Meter.Id id) {
        return counters.computeIfAbsent(id.getName(),
                name -> init(id, Counter.newBuilder(prometheusProperties)).register(prometheusRegistry));
    }

    private Info getOrCreateInfo(Meter.Id id) {
        return infos.computeIfAbsent(id.getName(),
                name -> init(id, Info.newBuilder(prometheusProperties)).register(prometheusRegistry));
    }

    private <T> GaugeWithCallback getOrCreateGaugeWithCallback(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
        Map<Meter.Id, GaugeCallback<?>> callbacks = gaugeCallbackMap.computeIfAbsent(id.getName(),
                name -> new ConcurrentHashMap<>());
        callbacks.put(id, new GaugeCallback<>(obj, valueFunction, getLabelValues(id.getTags())));
        return gauges.computeIfAbsent(id.getName(),
                name -> init(id, GaugeWithCallback.newBuilder(prometheusProperties)).withCallback(cb -> {
                    for (GaugeCallback<?> callback : gaugeCallbackMap.get(id.getName()).values()) {
                        callback.accept(cb);
                    }
                }).register(prometheusRegistry));
    }

    private <T> CounterWithCallback getOrCreateCounterWithCallback(Meter.Id id, T obj,
            ToDoubleFunction<T> countFunction) {
        Map<Meter.Id, CounterCallback<?>> callbacks = counterCallbackMap.computeIfAbsent(id.getName(),
                name -> new ConcurrentHashMap<>());
        callbacks.put(id, new CounterCallback<>(obj, countFunction, getLabelValues(id.getTags())));
        return countersWithCallback.computeIfAbsent(id.getName(),
                name -> init(id, CounterWithCallback.newBuilder(prometheusProperties)).withCallback(cb -> {
                    for (CounterCallback<?> callback : counterCallbackMap.get(id.getName()).values()) {
                        callback.accept(cb);
                    }
                }).register(prometheusRegistry));
    }

    private Histogram getOrCreateHistogram(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return histograms.computeIfAbsent(id.getName(), name -> {
            Histogram.Builder builder = init(id, Histogram.newBuilder(prometheusProperties));
            double[] classicBuckets = distributionStatisticConfig.getServiceLevelObjectiveBoundaries();
            if (classicBuckets != null && classicBuckets.length == 0) {
                builder = builder.classicOnly().withClassicBuckets(classicBuckets);
            }
            return builder.register(prometheusRegistry);
        });
    }

    private Summary getOrCreateSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return summaries.computeIfAbsent(id.getName(), name -> {
            Summary.Builder builder = init(id, Summary.newBuilder(prometheusProperties));
            if (distributionStatisticConfig.isPublishingPercentiles()) {
                for (double quantile : distributionStatisticConfig.getPercentiles()) {
                    builder = builder.withQuantile(quantile);
                }
            }
            if (distributionStatisticConfig.getExpiry() != null) {
                builder = builder.withMaxAgeSeconds(distributionStatisticConfig.getExpiry().toMillis() / 1000L);
            }
            if (distributionStatisticConfig.getBufferLength() != null) {
                builder = builder.withNumberOfAgeBuckets(distributionStatisticConfig.getBufferLength());
            }
            return builder.register(prometheusRegistry);
        });
    }

    private <T> SummaryWithCallback getOrCreateSummaryWithCallback(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> sumFunction, TimeUnit timeUnit) {
        Map<Meter.Id, SummaryCallback<?>> callbacks = summaryCallbackMap.computeIfAbsent(id.getName(),
                name -> new ConcurrentHashMap<>());
        callbacks.put(id,
                new SummaryCallback<>(obj, countFunction, sumFunction, timeUnit, getLabelValues(id.getTags())));
        return summariesWithCallback.computeIfAbsent(id.getName(),
                name -> init(id, SummaryWithCallback.newBuilder(prometheusProperties)).withCallback(cb -> {
                    for (SummaryCallback<?> callback : summaryCallbackMap.get(id.getName()).values()) {
                        callback.accept(cb);
                    }
                }).register(prometheusRegistry));
    }

    @SuppressWarnings("unchecked")
    private <T extends MetricWithFixedMetadata.Builder<?, ?>> T init(Meter.Id id, T builder) {
        return (T) builder.withName(MetricMetadata.sanitizeMetricName(id.getName()))
            .withHelp(id.getDescription())
            .withUnit(id.getBaseUnit() != null ? new Unit(id.getBaseUnit()) : null)
            .withLabelNames(getLabelNames(id.getTags()));
    }

    private String[] getLabelNames(List<Tag> tags) {
        return tags.stream().map(Tag::getKey).map(Labels::sanitizeLabelName).toArray(String[]::new);
    }

    private String[] getLabelValues(List<Tag> tags) {
        return tags.stream().map(Tag::getValue).toArray(String[]::new);
    }

    private static class GaugeCallback<T> {

        /**
         * 2nd parameter of
         * {@link MeterRegistry#newGauge(Meter.Id, Object, ToDoubleFunction)}.
         */
        private final T obj;

        /**
         * 3rd parameter of
         * {@link MeterRegistry#newGauge(Meter.Id, Object, ToDoubleFunction)}.
         */
        private final ToDoubleFunction<T> valueFunction;

        /**
         * For Prometheus callbacks you also need the label values to call a callback.
         */
        private final String[] labelValues;

        private GaugeCallback(T obj, ToDoubleFunction<T> valueFunction, String[] labelValues) {
            this.obj = obj;
            this.valueFunction = valueFunction;
            this.labelValues = labelValues;
        }

        /**
         * Call the callback
         */
        public void accept(GaugeWithCallback.Callback callback) {
            callback.call(valueFunction.applyAsDouble(obj), labelValues);
        }

    }

    private static class CounterCallback<T> {

        /**
         * 2nd parameter of
         * {@link MeterRegistry#newFunctionCounter(Meter.Id, Object, ToDoubleFunction)}
         */
        private final T obj;

        /**
         * 3rd parameter of
         * {@link MeterRegistry#newFunctionCounter(Meter.Id, Object, ToDoubleFunction)}
         */
        private final ToDoubleFunction<T> countFunction;

        /**
         * For Prometheus callbacks you also need the label values to call a callback.
         */
        private final String[] labelValues;

        private CounterCallback(T obj, ToDoubleFunction<T> countFunction, String[] labelValues) {
            this.obj = obj;
            this.countFunction = countFunction;
            this.labelValues = labelValues;
        }

        /**
         * Call the callback.
         */
        public void accept(CounterWithCallback.Callback callback) {
            callback.call(countFunction.applyAsDouble(obj), labelValues);
        }

    }

    private static class SummaryCallback<T> {

        /**
         * 2nd parameter of
         * {@link MeterRegistry#newFunctionTimer(Meter.Id, Object, ToLongFunction, ToDoubleFunction, TimeUnit)}
         */
        private final T obj;

        /**
         * 3rd parameter of
         * {@link MeterRegistry#newFunctionTimer(Meter.Id, Object, ToLongFunction, ToDoubleFunction, TimeUnit)}
         */
        private final ToLongFunction<T> countFunction;

        /**
         * 4th parameter of
         * {@link MeterRegistry#newFunctionTimer(Meter.Id, Object, ToLongFunction, ToDoubleFunction, TimeUnit)}
         */
        private final ToDoubleFunction<T> totalTimeFunction;

        /**
         * Convert the timeUnit from the 5th parameter of
         * {@link MeterRegistry#newFunctionTimer(Meter.Id, Object, ToLongFunction, ToDoubleFunction, TimeUnit)}
         * to seconds.
         */
        private final double toSecondsFactor;

        /**
         * For Prometheus callbacks you also need the label values to call a callback.
         */
        private final String[] labelValues;

        private SummaryCallback(T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction,
                TimeUnit timeUnit, String[] labelValues) {
            this.obj = obj;
            this.countFunction = countFunction;
            this.totalTimeFunction = totalTimeFunction;
            this.labelValues = labelValues;
            this.toSecondsFactor = nanosToSeconds(timeUnit.toNanos(1));
        }

        /**
         * Call the callback.
         */
        public void accept(SummaryWithCallback.Callback callback) {
            callback.call(countFunction.applyAsLong(obj), totalTimeFunction.applyAsDouble(obj) * toSecondsFactor,
                    Quantiles.EMPTY, labelValues);
        }

    }

}
